package com.ssafy.eolssu.backend.feature.auth.infrastructure.persistence

import com.ssafy.eolssu.backend.feature.auth.domain.*
import com.ssafy.eolssu.backend.feature.user.infrastructure.persistence.UserMapper
import com.ssafy.eolssu.backend.feature.user.infrastructure.persistence.UserRow
import java.time.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

class MyBatisSocialAuthRepository(private val users:UserMapper,private val jdbc:JdbcTemplate,manager:PlatformTransactionManager):SocialAuthRepository {
    private val transaction=TransactionTemplate(manager)
    override fun <T:Any> inTransaction(action:()->T):T=checkNotNull(transaction.execute{action()})
    override fun memberByEmail(email:String)=users.findSignInMember(email)?.member()
    override fun member(userId:Long,lock:Boolean)=(if(lock)users.findByIdForUpdate(userId) else users.findById(userId))?.member()
    override fun linked(provider:String,subject:String)=jdbc.query("SELECT user_id,provider,provider_user_id,created_at FROM user_social_accounts WHERE provider=? AND provider_user_id=?",{ row,_->
        SocialLink(row.getLong("user_id"),row.getString("provider"),row.getString("provider_user_id"),row.getObject("created_at",LocalDateTime::class.java).toInstant(ZoneOffset.UTC))
    },provider,subject).singleOrNull()
    override fun links(userId:Long)=jdbc.query("SELECT user_id,provider,provider_user_id,created_at FROM user_social_accounts WHERE user_id=? ORDER BY created_at,id",{ row,_->
        SocialLink(row.getLong("user_id"),row.getString("provider"),row.getString("provider_user_id"),row.getObject("created_at",LocalDateTime::class.java).toInstant(ZoneOffset.UTC))
    },userId)
    override fun link(userId:Long,provider:String,subject:String,now:Instant) {
        jdbc.update("INSERT INTO user_social_accounts(user_id,provider,provider_user_id,created_at) VALUES (?,?,?,?)",userId,provider,subject,now.utc())
    }
    override fun unlink(userId:Long,provider:String)=jdbc.update("DELETE FROM user_social_accounts WHERE user_id=? AND provider=?",userId,provider)==1
    override fun completeLogin(userId:Long,now:Instant) {
        users.cancelWithdrawal(userId,now.utc())
        check(users.recordLogin(userId,now.utc())==1)
    }
    private fun UserRow.member():SocialMember? {
        if(status !in setOf("ACTIVE","WITHDRAWAL_PENDING") || email==null || passwordHash==null || nickname==null) return null
        return SocialMember(email,SignInMember(id,passwordHash,nickname,instrumentId,notationMode,MemberStatus.valueOf(status)))
    }
    private fun Instant.utc()=LocalDateTime.ofInstant(this,ZoneOffset.UTC)
}
