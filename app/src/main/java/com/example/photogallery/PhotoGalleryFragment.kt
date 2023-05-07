package com.example.photogallery

import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.media.Image
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.RemoteViews.RemoteCollectionItems
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import androidx.work.*
import com.example.photogallery.api.FlickrApi
import org.w3c.dom.Text
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.Objects
import java.util.concurrent.TimeUnit

private const val TAG = "PhotoGalleryFragment"
private const val POLL_WORK = "POLL_WORK"

class PhotoGalleryFragment : VisibleFragment() {

    private lateinit var photoRecyclerView: RecyclerView
    private lateinit var photoGalleryViewModel: PhotoGalleryViewModel
    private lateinit var thumbnailDownloader: ThumbnailDownloader<PhotoHolder>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        val retrofit: Retrofit = Retrofit.Builder().baseUrl("https://www.flickr.com/").addConverterFactory(ScalarsConverterFactory.create()).build()
//        val flickrApi: FlickrApi = retrofit.create(FlickrApi::class.java)
//        //注意，调用FlickrApi的fetchcontents函数并不是执行网络请求，而是返回一个代表网络请求的call<String>对象，然后，由你决定何时执行这个Call对象，基于你创建的API接口（FlickrApi）和Retrofit对象
//        //Retrofit决定Call对象的内部细节
//        val flickrHomePageRequest: Call<String> = flickrApi.fetchContents()
//        flickrHomePageRequest.enqueue(object : Callback<String> {
//            override fun onFailure(call: Call<String>, t: Throwable) {
//                Log.e(TAG, "Failed to fetch photos", t)
//            }
//
//            override fun onResponse(call: Call<String>, response: Response<String>) {
//                Log.d(TAG, "Response received: ${response.body()}")
//            }
//        })

//        val flickrLiveData: LiveData<List<GalleryItem>> = FlickrFetchr().fetchPhotos()
//        flickrLiveData.observe(this, Observer { responseString -> Log.d(TAG, "Response received: $responseString")})

        /**
         * 首次向某个指定声明周期所有者请求ViewModel时，一个ViewModel新实例会被创建，由于发生像设备旋转这样的设备配置改变时PhotoGalleryFragment会被销毁后重建，因此原来的ViewModel会保留下来
         * 随后在请求ViewModel时，回取得最初创建的同一ViewModel实例。
         */
        photoGalleryViewModel = ViewModelProviders.of(this).get(PhotoGalleryViewModel::class.java)

        retainInstance = true
        setHasOptionsMenu(true)
        val responseHandler = Handler()
        thumbnailDownloader = ThumbnailDownloader(responseHandler) {photoHolder, bitmap ->
            val drawable = BitmapDrawable(resources, bitmap)
            photoHolder.bindDrawable(drawable)}
        lifecycle.addObserver(thumbnailDownloader.fragmentLifecycleObserver)

//        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()
//
//        val workRequest = OneTimeWorkRequest.Builder(PollWorker::class.java).setConstraints(constraints).build()
//        WorkManager.getInstance().enqueue(workRequest)


        /**
         * 既然Fragment实现了lifecycleOwner接口，他因此会有一个lifecycle属性。你可以用这个属性把观察者添加给fragment的lifecycle.调用
         * lifecycle.addObserver(thumbnailDownloader)函数就能登记，lifecycle.addObserver(thumbnailDownloader)函数就能登记
         *
         * 现在，PhotoGalleryFragment.onCreate(...)被调用时，就会触发ThumbnailDownloader.setup()的调用。
         * PhotoGalleryFragment.onDestroy()被调用时，就会触发ThumbnailDownloader.tearDown()的调用。
         */

        //Retrofit天生就遵循着两个最重要的android多线程规则
        //1.仅在后台线程上执行耗时任务
        //2.仅在主线程上做UI更新操作
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)
        if (inflater != null) {
            inflater.inflate(R.menu.fragment_photo_gallery, menu)
        }

        val searchItem: MenuItem = menu!!.findItem(R.id.menu_item_search)
        val searchView = searchItem.actionView as SearchView
        searchView.apply {

            setOnSearchClickListener {
                searchView.setQuery(photoGalleryViewModel.searchTerm, false)
            }

            setOnQueryTextListener(object : SearchView.OnQueryTextListener {

                /**
                 * 当用户提交搜索查询时，这个函数就会执行
                 */
                override fun onQueryTextSubmit(queryText: String): Boolean {
                    Log.d(TAG, "QueryTextSubmit: $queryText")
                    photoGalleryViewModel.fetchPhotos(queryText)
                    return true
                }

                /**
                 * 只要搜索框中的文字有变化，onQueryTextChange(String)回调函数就会执行。在PhotoGallery应用中，
                 * 除了记日志和返回false值，这个回调函数不会做其他任何事。返回false值是告诉系统，回调覆盖函数响应了搜索指令变化但没有做出处理
                 */
                override fun onQueryTextChange(queryText: String): Boolean {
                    Log.d(TAG, "QueryTextChange: $queryText")
                    return false
                }
            })
        }

        val toggleItem = menu.findItem(R.id.menu_item_toggle_polling)
        val isPolling = QueryPreferences.isPolling(requireContext())
        val toggleItemTitle = if (isPolling) {
            R.string.stop_polling
        } else {
            R.string.start_polling
        }
        toggleItem.setTitle(toggleItemTitle)

    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when (item?.itemId) {
            R.id.menu_item_clear -> {
                photoGalleryViewModel.fetchPhotos("")
                true
            }
            R.id.menu_item_toggle_polling -> {
                val isPolling = QueryPreferences.isPolling(requireContext())
                if (isPolling) {
                    WorkManager.getInstance().cancelUniqueWork(POLL_WORK)
                    QueryPreferences.setPolling(requireContext(), false)
                } else {
                    val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()
                    val periodicRequest = PeriodicWorkRequest.Builder(PollWorker::class.java, 15, TimeUnit.MINUTES).setConstraints(constraints)
                        .build()
                    WorkManager.getInstance().enqueueUniquePeriodicWork(POLL_WORK, ExistingPeriodicWorkPolicy.KEEP, periodicRequest)
                    QueryPreferences.setPolling(requireContext(), true)
                }
                activity?.invalidateOptionsMenu()
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewLifecycleOwner.lifecycle.addObserver(thumbnailDownloader.viewLifecycleObserver)
        val view = inflater.inflate(R.layout.fragment_photo_gallery, container, false)
        photoRecyclerView = view.findViewById(R.id.photo_recycler_view)
        photoRecyclerView.layoutManager = GridLayoutManager(context, 3)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        photoGalleryViewModel.galleryItemLiveData.observe(viewLifecycleOwner, Observer {galleryItems -> Log.d(
            TAG, "Have gallery items from VIewModel $galleryItems")
            photoRecyclerView.adapter = PhotoAdapter(galleryItems)
        })
    }

    override fun onDestroy() {
        super.onDestroy()
//        lifecycle.removeObserver(thumbnailDownloader)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewLifecycleOwner.lifecycle.removeObserver(thumbnailDownloader.viewLifecycleObserver)
    }

    private inner class PhotoHolder(private val itemImageView: ImageView) : RecyclerView.ViewHolder(itemImageView), View.OnClickListener {
        private lateinit var galleryItem: GalleryItem
        init {
            itemImageView.setOnClickListener(this)
        }
        val bindDrawable:(Drawable) -> Unit = itemImageView::setImageDrawable
        fun bindGalleryItem(item: GalleryItem) {
            galleryItem = item
        }
        override fun onClick(p0: View?) {
//            val intent = Intent(Intent.ACTION_VIEW, galleryItem.photoPageUri)
            val intent = PhotoPageActivity.newIntent(requireContext(), galleryItem.photoPageUri)
            startActivity(intent)
        }
    }

    private inner class PhotoAdapter(private val galleryItems: List<GalleryItem>) : RecyclerView.Adapter<PhotoHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoHolder {
            val view = layoutInflater.inflate(R.layout.list_item_gallery, parent, false) as ImageView
            //在 LayoutInflater.inflate() 方法中，第三个参数表示是否将生成的 View 添加到 ViewGroup 中。如果是在 RecyclerView.Adapter 的 onCreateViewHolder() 方法中使用，
            // 一般都是设置为 false，因为在 RecyclerView 的布局过程中会自动将 View 添加到 RecyclerView 中。如果设为 true，则需要手动添加 View 到父布局中。
            return PhotoHolder(view)
        }

        override fun onBindViewHolder(holder: PhotoHolder, position: Int) {
            val galleryItem = galleryItems[position]
            holder.bindGalleryItem(galleryItem)
            val placeHolder: Drawable = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.bill_up_close
            ) ?: ColorDrawable()
            holder.bindDrawable(placeHolder)
            thumbnailDownloader.queueThumbnail(holder, galleryItem.url)
        }

        override fun getItemCount(): Int {
            return galleryItems.size
        }

    }

    companion object {
        fun newInstance() = PhotoGalleryFragment()
        private const val TAG = "PhotoGalleryFragment"
    }
}