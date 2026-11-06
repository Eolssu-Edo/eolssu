package com.ssafy.eolssu.core.audio
import kotlin.math.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
class PitchAccuracyMeasurementTest {
 @Test fun knownInputs() {
  for (mode in 0..3) {
   val errors = mutableListOf<Double>()
   var missing=0
   for (hz in listOf(110.0,220.0,261.625565,440.0,523.251131,880.0,1046.502261,1760.0,2093.004522,2500.0)) for (phase in listOf(0.0,0.7,1.9,3.1)) {
    val random=Random(515)
    val samples=FloatArray(2048) { i ->
     val t=2*PI*hz*i/44100+phase
     when(mode) {
      1 -> 0.3*sin(t)+0.1*sin(2*t)+0.05*sin(3*t)
      2 -> 0.08*sin(t)+0.4*sin(2*t)+0.03*sin(3*t)
      3 -> 0.3*sin(t)+(random.nextDouble()*2-1)*0.036742346
      else -> 0.3*sin(t)
     }.toFloat()
    }
    val found=YinPitchEstimator.detect(samples,44100)
    if(found==null) missing++ else errors.add(abs(1200*ln(found/hz)/ln(2.0)))
   }
   println("PITCH mode=$mode total=40 missing=$missing octave=${errors.count{it>=600}} within20=${errors.count{it<=20}} max=${errors.maxOrNull()} mean=${errors.average()}")
   if(mode==0) assertTrue(missing==0 && errors.all{it<20})
  }
  var falsePositive=0
  for(seed in 0 until 40) {
   val random=Random(seed)
   if(YinPitchEstimator.detect(FloatArray(2048){(random.nextDouble()*0.8-0.4).toFloat()},44100)!=null) falsePositive++
  }
  println("PITCH noiseOnly=40 falsePositive=$falsePositive")
 }
}
