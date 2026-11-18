package com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence

import java.time.LocalDateTime
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

/** 탈퇴 일괄 삭제. 순서와 대상은 저장소 DB 스키마 계약 '탈퇴 일괄 삭제 대상'을 따른다. */
@Mapper
interface WithdrawalPurgeMapper {
    fun stopSheetConversions(@Param("userId") userId: Long): Int
    fun findDueMembers(@Param("now") now: LocalDateTime, @Param("afterUserId") afterUserId: Long, @Param("limit") limit: Int): List<Long>
    fun findRecordingKeys(@Param("userId") userId: Long): List<String>

    fun findSheetSourceKeys(@Param("userId") userId: Long): List<String>
    /** 아직 삭제 대상이면 회원 행을 잠그고 이메일을 돌려준다. */
    fun lockDueMember(@Param("userId") userId: Long, @Param("now") now: LocalDateTime): PurgeMemberRow?
    fun lockSessions(@Param("userId") userId: Long): List<Long>
    fun deleteNoteResults(@Param("userId") userId: Long): Int
    fun deleteEvaluations(@Param("userId") userId: Long): Int
    fun deleteSessions(@Param("userId") userId: Long): Int
    fun deleteScoreViews(@Param("userId") userId: Long): Int
    fun deleteTermAgreements(@Param("userId") userId: Long): Int
    fun deleteSongSettings(@Param("userId") userId: Long): Int
    fun deleteLessonProgress(@Param("userId") userId: Long): Int
    fun deleteUserSheets(@Param("userId") userId: Long): Int
    fun deleteRefreshTokens(@Param("userId") userId: Long): Int
    fun deleteSocialAccounts(@Param("userId") userId: Long): Int
    fun deleteEmailVerifications(@Param("email") email: String): Int
    fun anonymize(@Param("userId") userId: Long, @Param("now") now: LocalDateTime): Int
}

/** 행 부재와 이메일 미등록을 구분한다. */
data class PurgeMemberRow(val id: Long, val email: String?)
