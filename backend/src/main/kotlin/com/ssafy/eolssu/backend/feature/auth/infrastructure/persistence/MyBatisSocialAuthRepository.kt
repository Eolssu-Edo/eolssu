package com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.auth.domain.*
import com.ssafy.eolssu.backend.feature.user.infrastructure.persistence.UserMapper
import com.ssafy.eolssu.backend.feature.user.infrastructure.persistence.UserRow
import java.time.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

class MyBatisSocialAuthRepository(private val users:UserMapper,private val jdbc:JdbcTemplate,manager:PlatformTransactionManager,private val subjects:KakaoSubjectLocks):SocialAuthRepository {
    private val transaction=TransactionTemplate(manager)
    override fun <T:Any> inTransaction(action:()->T):T=checkNotNull(transaction.execute{action()})
    override fun memberByEmail(email:String)=users.findSignInMember(email)?.member()
    override fun member(userId:Long,lock:Boolean)=(if(lock)users.findByIdForUpdate(userId) else users.findById(userId))?.member()
    override fun linked(provider:String,subject:String)=jdbc.query("SELECT id,user_id,provider,provider_user_id,created_at FROM user_social_accounts WHERE provider=? AND provider_user_id=?",{ row,_->
        SocialLink(row.getLong("user_id"),row.getString("provider"),row.getString("provider_user_id"),row.getObject("created_at",LocalDateTime::class.java).toInstant(ZoneOffset.UTC),row.getLong("id"))
    },provider,subject).singleOrNull()
    override fun links(userId:Long)=jdbc.query("SELECT id,user_id,provider,provider_user_id,created_at FROM user_social_accounts WHERE user_id=? ORDER BY created_at,id",{ row,_->
        SocialLink(row.getLong("user_id"),row.getString("provider"),row.getString("provider_user_id"),row.getObject("created_at",LocalDateTime::class.java).toInstant(ZoneOffset.UTC),row.getLong("id"))
    },userId)
    override fun lockKakaoSubject(subject:String)=subjects.lock(subject)
    override fun link(userId:Long,provider:String,subject:String,now:Instant,flowStartedAt:Instant?,providerToken:String?) {
        jdbc.update("INSERT INTO user_social_accounts(user_id,provider,provider_user_id,created_at,oauth_started_at,provider_token) VALUES (?,?,?,?,?,?)",userId,provider,subject,now.utc(),flowStartedAt?.utc(),providerToken)
    }
    override fun saveProviderToken(linkId:Long,sealed:String) {
        jdbc.update("UPDATE user_social_accounts SET provider_token=? WHERE id=?",sealed,linkId)
    }
    override fun providerTokens(userId:Long)=jdbc.query("SELECT id,provider,provider_token FROM user_social_accounts WHERE user_id=? AND provider_token IS NOT NULL ORDER BY id",{ row,_->
        StoredProviderToken(row.getLong("id"),row.getString("provider"),row.getString("provider_token"))
    },userId)
    override fun clearProviderToken(linkId:Long,sealed:String)=jdbc.update("UPDATE user_social_accounts SET provider_token=NULL WHERE id=? AND provider_token=?",linkId,sealed)==1
    override fun unlink(userId:Long,provider:String)=jdbc.update("DELETE FROM user_social_accounts WHERE user_id=? AND provider=?",userId,provider)==1
    override fun completeLogin(userId:Long,now:Instant) {
        users.cancelWithdrawal(userId,now.utc(),now.minus(WithdrawalPolicy.RETENTION).utc())
        check(users.recordLogin(userId,now.utc())==1)
    }
    private fun UserRow.member():SocialMember? {
        if(status !in setOf("ACTIVE","WITHDRAWAL_PENDING") || nickname==null) return null
        return SocialMember(email,SignInMember(id,passwordHash,nickname,instrumentId,notationMode,MemberStatus.valueOf(status),withdrawalRequestedAt?.toInstant(ZoneOffset.UTC)))
    }
    private fun Instant.utc()=LocalDateTime.ofInstant(this,ZoneOffset.UTC)
}
