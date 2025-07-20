package com.japyi0210.simpleenglishdictation

import android.app.Activity
import android.app.Application
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.LoadAdError

class MyApplication : Application() {

    private var appOpenAd: AppOpenAd? = null
    private var isShowingAd = false
    private var isLoadingAd = false

    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this)
        loadAd() // 초기 로드
    }

    /** 광고를 로드함 */
    private fun loadAd() {
        if (isLoadingAd || appOpenAd != null) {
            Log.d("AppOpenAd", "🚫 광고 로딩 스킵 (이미 로딩 중이거나 존재함)")
            return
        }

        isLoadingAd = true
        val request = AdRequest.Builder().build()

        Log.d("AppOpenAd", "📡 AppOpen 광고 로드 요청 시작")

        AppOpenAd.load(
            this,
            APP_OPEN_AD_UNIT_ID,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d("AppOpenAd", "✅ 광고 로드 성공")
                    appOpenAd = ad
                    isLoadingAd = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("AppOpenAd", "❌ 광고 로드 실패: ${error.message}")
                    appOpenAd = null
                    isLoadingAd = false
                }
            }
        )
    }

    /** 광고가 준비됐을 때만 바로 보여주고, 없으면 먼저 로드 후 표시 */
    fun loadAdAndShowIfAvailable(activity: Activity, onAdComplete: () -> Unit) {
        if (appOpenAd != null) {
            showAdIfAvailable(activity, onAdComplete)
        } else {
            val request = AdRequest.Builder().build()
            Log.d("AppOpenAd", "📡 광고 없어서 로드 후 show 대기")

            AppOpenAd.load(
                this,
                APP_OPEN_AD_UNIT_ID,
                request,
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        Log.d("AppOpenAd", "✅ 광고 로드 성공 → show 실행")
                        appOpenAd = ad
                        showAdIfAvailable(activity, onAdComplete)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.e("AppOpenAd", "❌ 광고 로드 실패: ${error.message}")
                        onAdComplete() // 실패 시 그냥 앱 진행
                    }
                }
            )
        }
    }

    /** 광고가 준비된 경우에만 표시 */
    private fun showAdIfAvailable(activity: Activity, onAdComplete: () -> Unit) {
        if (appOpenAd != null && !isShowingAd) {
            Log.d("AppOpenAd", "🎬 광고 표시 시작")

            isShowingAd = true

            appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d("AppOpenAd", "👋 광고 닫힘")
                    appOpenAd = null
                    isShowingAd = false
                    loadAd() // 다음 광고 미리 로드
                    onAdComplete()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e("AppOpenAd", "❌ 광고 표시 실패: ${adError.message}")
                    isShowingAd = false
                    onAdComplete()
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d("AppOpenAd", "📺 광고 화면 표시됨")
                }
            }

            appOpenAd?.show(activity)
        } else {
            Log.d("AppOpenAd", "🚫 광고 없음 또는 이미 표시 중")
            onAdComplete()
        }
    }

    companion object {
        private const val APP_OPEN_AD_UNIT_ID = "ca-app-pub-1872760638277957/5850323907"
    }
}
