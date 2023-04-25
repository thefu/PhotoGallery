package com.example.photogallery


import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class PhotoGalleryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_gallery)

        val isFragmentContainerEmpty = savedInstanceState == null
        //这个检查很有必要，因为发生设备配置改变，或者出现系统强杀应用进程后，fragment管理器会自动重建被托管fragment并将其添加给activity
        //如果bundle数据为空，说明托管activity刚启动，不会有fragment的重建和再托管，如果bundle数据不为空，说明托管activity正在重建（设备旋转或者进程被杀死），
        // 自然它被杀死之前托管的fragment也会被重建添加回来
        if (isFragmentContainerEmpty) {
            supportFragmentManager.beginTransaction().add(R.id.fragmentContainer, PhotoGalleryFragment.newInstance()).commit()
        }
    }

    companion object {
        fun newIntent(context: Context): Intent {
            return Intent(context, PhotoGalleryActivity::class.java)
        }
    }
}