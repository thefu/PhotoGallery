package com.example.photogallery.api

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

private const val API_KEY = "fb507107b68e9e1ea1371946038c430d"

class PhotoInterceptor : Interceptor {


    /**
     * 首先调用chain.request()获取到原始网络请求，然后，使用originalRequest.url()函数从原始网络请求中取出URL，再使用HttpUrl。Builder添加需要的查询参数，并创建出新的网络请求。
     * 最后，调用chain.proceed(newRequest)函数产生网络响应消息
     */
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest: Request = chain.request()

        val newUrl: HttpUrl = originalRequest.url().newBuilder()
            .addQueryParameter("api_key", API_KEY)
            .addQueryParameter("format", "json")
            .addQueryParameter("nojsoncallback", "1")
            .addQueryParameter("extras", "url_s")
            .addQueryParameter("safesearch", "1")
            .build()

        val newRequest: Request = originalRequest.newBuilder()
            .url(newUrl)
            .build()

        return chain.proceed(newRequest)
    }
}