package com.example.photogallery.api

import com.example.photogallery.FlickrResponse
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface FlickrApi {

    /**
     * 这里注解的作用是把fetchContents()函数返回的call配置成一个Get请求。字符串“/”表示一个相对路径URL-针对Flickr API端点基URL来说的相对路径
     */
    @GET("/")
    fun fetchContents(): Call<String>

    @GET(
        "services/rest/?method=flickr.interestingness.getList" +
                "&api_key=fb507107b68e9e1ea1371946038c430d" +
                "&format=json" +
                "&nojsoncallback=1" +
                "&extras=url_s"
    )
    fun fetchPhotos(): Call<FlickrResponse>

    @GET
    fun fetchUrlBytes(@Url url: String): Call<ResponseBody>

    /**
     * @Query注解允许你动态拼接查询参数后再拼接到URL串里
     */
    @GET("services/rest?method=flickr.photos.search")
    fun searchPhotos(@Query("text") query: String): Call<FlickrResponse>
}


