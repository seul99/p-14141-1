package com.back.boundedContexts.member.`in`.shared

import com.back.boundedContexts.member.app.MemberFacade
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler
import org.springframework.transaction.annotation.Transactional

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiV1AuthControllerTest {
    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var memberFacade: MemberFacade

    @Test
    fun `로그인 요청이 성공하면 회원 정보와 토큰 그리고 쿠키를 반환한다`() {
        val resultActions = mvc.post("/member/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                    "username": "user1",
                    "password": "1234"
                }
                """.trimIndent()
        }

        val member = memberFacade.findByUsername("user1")!!

        resultActions.andExpect {
            status { isOk() }
            match(handler().handlerType(ApiV1AuthController::class.java))
            match(handler().methodName("login"))
            jsonPath("$.resultCode") { value("200-1") }
            jsonPath("$.msg") { value("${member.nickname}님 환영합니다.") }
            jsonPath("$.data.item.id") { value(member.id) }
            jsonPath("$.data.item.createdAt") { value(startsWith(member.createdAt.toString().take(20))) }
            jsonPath("$.data.item.modifiedAt") { value(startsWith(member.modifiedAt.toString().take(20))) }
            jsonPath("$.data.item.name") { value(member.nickname) }
            jsonPath("$.data.apiKey") { value(member.apiKey) }
            jsonPath("$.data.accessToken") { exists() }
        }

        val result = resultActions.andReturn()

        val apiKeyCookie = result.response.getCookie("apiKey")
        assertThat(apiKeyCookie).isNotNull
        assertThat(apiKeyCookie!!.value).isEqualTo(member.apiKey)
        assertThat(apiKeyCookie.path).isEqualTo("/")
        assertThat(apiKeyCookie.isHttpOnly).isTrue

        val accessTokenCookie = result.response.getCookie("accessToken")
        assertThat(accessTokenCookie).isNotNull
        assertThat(accessTokenCookie!!.value).isNotBlank()
        assertThat(accessTokenCookie.path).isEqualTo("/")
        assertThat(accessTokenCookie.isHttpOnly).isTrue
    }

    @Test
    fun `로그인 요청에서 비밀번호가 틀리면 401을 반환한다`() {
        mvc.post("/member/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                    "username": "user1",
                    "password": "wrong-password"
                }
                """.trimIndent()
        }.andExpect {
            status { isUnauthorized() }
            match(handler().handlerType(ApiV1AuthController::class.java))
            match(handler().methodName("login"))
            jsonPath("$.resultCode") { value("401-1") }
            jsonPath("$.msg") { value("비밀번호가 일치하지 않습니다.") }
        }
    }

    @Test
    fun `로그인 요청에서 존재하지 않는 username 을 보내면 401을 반환한다`() {
        mvc.post("/member/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                    "username": "nonexistent",
                    "password": "1234"
                }
                """.trimIndent()
        }.andExpect {
            status { isUnauthorized() }
            match(handler().handlerType(ApiV1AuthController::class.java))
            match(handler().methodName("login"))
            jsonPath("$.resultCode") { value("401-1") }
            jsonPath("$.msg") { value("존재하지 않는 아이디입니다.") }
        }
    }

    @Test
    fun `로그아웃 요청이 성공하면 인증 쿠키를 만료시킨다`() {
        val resultActions = mvc.delete("/member/api/v1/auth/logout")
            .andExpect {
                status { isOk() }
                match(handler().handlerType(ApiV1AuthController::class.java))
                match(handler().methodName("logout"))
                jsonPath("$.resultCode") { value("200-1") }
                jsonPath("$.msg") { value("로그아웃 되었습니다.") }
            }

        val result = resultActions.andReturn()

        val apiKeyCookie: Cookie? = result.response.getCookie("apiKey")
        assertThat(apiKeyCookie).isNotNull
        assertThat(apiKeyCookie!!.value).isEmpty()
        assertThat(apiKeyCookie.maxAge).isEqualTo(0)
        assertThat(apiKeyCookie.path).isEqualTo("/")
        assertThat(apiKeyCookie.isHttpOnly).isTrue

        val accessTokenCookie: Cookie? = result.response.getCookie("accessToken")
        assertThat(accessTokenCookie).isNotNull
        assertThat(accessTokenCookie!!.value).isEmpty()
        assertThat(accessTokenCookie.maxAge).isEqualTo(0)
        assertThat(accessTokenCookie.path).isEqualTo("/")
        assertThat(accessTokenCookie.isHttpOnly).isTrue
    }

    @Test
    fun `내 정보 조회는 apiKey 쿠키가 있으면 회원 정보를 반환한다`() {
        val member = memberFacade.findByUsername("user1")!!

        mvc.get("/member/api/v1/auth/me") {
            cookie(Cookie("apiKey", member.apiKey))
        }.andExpect {
            status { isOk() }
            match(handler().handlerType(ApiV1AuthController::class.java))
            match(handler().methodName("me"))
            jsonPath("$.id") { value(member.id) }
            jsonPath("$.createdAt") { value(startsWith(member.createdAt.toString().take(20))) }
            jsonPath("$.modifiedAt") { value(startsWith(member.modifiedAt.toString().take(20))) }
            jsonPath("$.username") { value(member.username) }
            jsonPath("$.nickname") { value(member.nickname) }
        }
    }

    @Test
    fun `내 정보 조회는 apiKey 쿠키가 없으면 401을 반환한다`() {
        mvc.get("/member/api/v1/auth/me")
            .andExpect {
                status { isUnauthorized() }
                match(handler().handlerType(ApiV1AuthController::class.java))
                match(handler().methodName("me"))
                jsonPath("$.resultCode") { value("401-1") }
                jsonPath("$.msg") { value("로그인 후 이용해주세요.") }
            }
    }
}
