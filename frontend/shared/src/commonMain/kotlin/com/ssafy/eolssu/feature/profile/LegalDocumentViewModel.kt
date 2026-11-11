package com.ssafy.eolssu.feature.profile

import androidx.lifecycle.ViewModel
import com.ssafy.eolssu.data.user.PublishedLegalDocument
import com.ssafy.eolssu.data.user.TermsBlock
import com.ssafy.eolssu.data.user.TermsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 마이페이지 법적 문서 화면 상태. [body]는 문서 본문이며, 받지 못하면 [loading]이 끝난 뒤에도 null이다. */
data class LegalDocumentUiState(val body: List<TermsBlock>? = null, val loading: Boolean = true)

/**
 * 마이페이지 하단의 서비스 이용약관·개인정보 처리방침·계정·데이터 삭제 안내를 브라우저 대신 앱 안에서 보여 준다(S15P31E102-731).
 * 공개 문서 주소의 HTML을 약관 상세(AUTH-02)와 같은 방식([TermsRepository.loadDocument])으로 글 묶음으로 바꾼다.
 */
class LegalDocumentViewModel(private val document: PublishedLegalDocument, private val repository: TermsRepository) : ViewModel() {
    private val state = MutableStateFlow(LegalDocumentUiState())
    val uiState = state.asStateFlow()

    /** 문서를 받는다. 다시 부르면 처음부터 다시 받는다(다시 시도). */
    suspend fun load() {
        state.value = LegalDocumentUiState()
        state.value = LegalDocumentUiState(body = repository.loadDocument(document.url), loading = false)
    }
}
