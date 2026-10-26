package com.ssafy.eolssu.app.di

import com.ssafy.eolssu.feature.home.HomeViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** 앱 전체에서 쓰는 객체 등록. 화면은 `koinViewModel()`로 ViewModel을 받는다. */
val appModule = module {
    viewModelOf(::HomeViewModel)
}
