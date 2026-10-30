package com.ssafy.eolssu.backend.feature.auth.application

import java.util.concurrent.*

/** 키 조회·풀 대기·DB를 합친 대기 상한. 대기열 없이 네 작업만 허용한다. */
class KakaoEventDeadline(private val timeoutMillis:Long=2500):AutoCloseable {
    private val workers=ThreadPoolExecutor(0,4,30,TimeUnit.SECONDS,SynchronousQueue(),
        ThreadFactory{task->Thread(task,"kakao-security-event").apply{isDaemon=true}},ThreadPoolExecutor.AbortPolicy())
    fun run(action:()->Unit) {
        val future=try{workers.submit(Runnable{action()})}catch(_:RejectedExecutionException){throw KakaoEventUnavailable()}
        try{future.get(timeoutMillis,TimeUnit.MILLISECONDS)}
        catch(_:TimeoutException){future.cancel(true);throw KakaoEventUnavailable()}
        catch(_:InterruptedException){future.cancel(true);Thread.currentThread().interrupt();throw KakaoEventUnavailable()}
        catch(e:ExecutionException){throw (e.cause as? RuntimeException?:KakaoEventUnavailable())}
    }
    override fun close(){workers.shutdownNow()}
}
