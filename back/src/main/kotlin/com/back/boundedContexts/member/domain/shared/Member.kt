package com.back.boundedContexts.member.domain.shared

import com.back.global.app.AppConfig
import com.back.global.jpa.domain.AfterDDL
import com.back.global.jpa.domain.BaseTime
import com.back.boundedContexts.member.out.shared.MemberAttrRepository
import jakarta.persistence.*
import jakarta.persistence.GenerationType.SEQUENCE
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.NaturalId
import java.util.UUID

private const val PROFILE_IMG_URL = "profileImgUrl"

@Entity
@DynamicUpdate
@AfterDDL(
    """
    CREATE INDEX IF NOT EXISTS member_idx_created_at_desc
    ON member (created_at DESC)
"""
)
@AfterDDL(
    """
        CREATE INDEX IF NOT EXISTS member_idx_modified_at_desc
        ON member (modified_at DESC)
"""
)
@AfterDDL(
    """
    CREATE INDEX IF NOT EXISTS member_idx_pgroonga_username_nickname
    ON member USING pgroonga ((ARRAY["username"::text, "nickname"::text])
    pgroonga_text_array_full_text_search_ops_v2) WITH (tokenizer = 'TokenBigram')
    """
)
class Member(
    @field:Id
    @field:SequenceGenerator(name = "member_seq_gen", sequenceName = "member_seq", allocationSize = 50)
    @field:GeneratedValue(strategy = SEQUENCE, generator = "member_seq")
    override val id: Int = 0,

    @field:NaturalId
    @field:Column(unique = true, nullable = false)
    val username: String,

    @field:Column(nullable = true)
    var password: String? = null,

    @field:Column(nullable = false)
    var nickname: String,

    @field:Column(unique = true, nullable = false)
    var apiKey: String,
) : BaseTime(id), HasMember {
    constructor(id: Int) : this(id, "", null, "", "")

    constructor(id: Int, username: String, nickname: String) : this(id, username, null, nickname, "")

    constructor(username: String, password: String?, nickname: String) : this(
        0,
        username,
        password,
        nickname,
        UUID.randomUUID().toString(),
    )

    companion object {
        lateinit var attrRepository_: MemberAttrRepository
        val attrRepository by lazy { attrRepository_ }
    }

    override val member: Member
        get() = this

    override val name: String
        get() = nickname

    val isAdmin: Boolean
        get() = username in setOf("system", "admin")

    private val profileImgUrlAttr: MemberAttr
        get() = getOrPutAttr(PROFILE_IMG_URL) {
            attrRepository.findBySubjectAndName(this, PROFILE_IMG_URL)
                ?: MemberAttr(0, this, PROFILE_IMG_URL, "")
        }

    var profileImgUrl: String
        get() = profileImgUrlAttr.value
        set(value) {
            profileImgUrlAttr.value = value
            attrRepository.save(profileImgUrlAttr)
        }

    val profileImgUrlOrDefault: String
        get() = profileImgUrl
            .takeIf { it.isNotBlank() }
            ?: "https://placehold.co/600x600?text=U_U"

    val redirectToProfileImgUrlOrDefault: String
        get() = "${AppConfig.siteBackUrl}/member/api/v1/members/$id/redirectToProfileImg"

    fun modify(nickname: String, profileImgUrl: String?) {
        this.nickname = nickname
        profileImgUrl?.let { this.profileImgUrl = it }
    }

    fun modifyApiKey(apiKey: String) {
        this.apiKey = apiKey
    }
}
