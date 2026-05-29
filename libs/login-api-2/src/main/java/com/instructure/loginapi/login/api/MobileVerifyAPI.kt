/*
 * Copyright (C) 2020 - present Instructure, Inc.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, version 3 of the License.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.instructure.loginapi.login.api

import com.instructure.canvasapi2.StatusCallback
import com.instructure.canvasapi2.utils.APIHelper.paramIsNull
import com.instructure.canvasapi2.utils.ApiPrefs
import com.instructure.canvasapi2.utils.Logger.d
import com.instructure.canvasapi2.utils.RemoteConfigParam
import com.instructure.canvasapi2.utils.RemoteConfigUtils
import com.instructure.loginapi.login.model.DomainVerificationResult
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

object MobileVerifyAPI {

    internal interface OAuthInterface {
        @GET("mobile_verify.json")
        fun mobileVerify(@Query(value = "domain", encoded = false) domain: String?, @Query("user_agent") userAgent: String?): Call<DomainVerificationResult>
    }

    private fun getAuthenticationRetrofit(domain: String?) : Retrofit {
            val httpClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", ApiPrefs.userAgent)
                        .cacheControl(CacheControl.FORCE_NETWORK)
                        .build()
                    chain.proceed(request)
                }.build()

            val mobileVerifyBetaEnabled = RemoteConfigUtils.getString(
                    RemoteConfigParam.MOBILE_VERIFY_BETA_ENABLED)?.equals("true", ignoreCase = true)
                    ?: false

            // We only want to switch over to the beta mobile verify domain if the remote firebase config is true
            val baseUrl = if (mobileVerifyBetaEnabled && domain?.contains(".beta.") == true) {
                "https://canvas.beta.instructure.com/api/v1/"
            } else {
                "https://sso.canvaslms.com/api/v1/"
            }

            return Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(httpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
        }

    fun mobileVerify(domain: String?, callback: StatusCallback<DomainVerificationResult>) {
        if (paramIsNull(callback, domain)) {
            return
        }
        if (ApiPrefs.userAgent == "") {
            d("User agent must be set for this API to work correctly!")
            return
        }

        if (domain != null && (domain.equals("cool.ntu.edu.tw", ignoreCase = true) || domain.contains("cool.ntu.edu.tw", ignoreCase = true))) {
            val userAgent = ApiPrefs.userAgent
            val (clientId, clientSecret) = when {
                userAgent.contains("Teacher", ignoreCase = true) || userAgent.contains("androidTeacher", ignoreCase = true) -> {
                    Pair("170000000000465", "rvNbw2O47LYJyWfgp2d6VMcFSJi3JG1jh10p8ke8Lf6XKzCuvqhzvfIvFGWJN1Ox")
                }
                userAgent.contains("Parent", ignoreCase = true) || userAgent.contains("androidParent", ignoreCase = true) -> {
                    Pair("170000000000466", "W8ZYJOWIQ0Qw6qfSCi49ikd81nMWtLKQ9x1ykUqgz55XNTHubtSbw4K4eEsK05Fn")
                }
                else -> {
                    Pair("170000000000044", "3sxR3NtgXRfT9KdpWGAFQygq6O9RzLN021h2lAzhHUZEeSQ5XGV41Ddi5iutwW6f")
                }
            }
            val mockResult = DomainVerificationResult(
                authorized = true,
                resultCode = 0,
                clientId = clientId,
                clientSecret = clientSecret,
                url = "https://$domain/"
            )
            callback.onResponse(retrofit2.Response.success(mockResult), com.instructure.canvasapi2.utils.LinkHeaders(), com.instructure.canvasapi2.utils.ApiType.API)
            callback.onCallbackFinished(com.instructure.canvasapi2.utils.ApiType.API)
            return
        }

        val oAuthInterface = getAuthenticationRetrofit(domain).create(OAuthInterface::class.java)
        oAuthInterface.mobileVerify(domain, ApiPrefs.userAgent).enqueue(callback)
    }
}
