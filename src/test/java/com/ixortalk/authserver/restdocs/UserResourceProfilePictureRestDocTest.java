/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-present IxorTalk CVBA
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.ixortalk.authserver.restdocs;

import com.ixortalk.authserver.domain.User;
import com.ixortalk.authserver.repository.UserRepository;
import com.ixortalk.aws.s3.library.config.AwsS3Template;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import javax.inject.Inject;
import java.io.IOException;

import static com.ixortalk.authserver.domain.UserTestBuilder.aUser;
import static com.ixortalk.authserver.web.rest.UserResourceProfilePictureIntTest.BINARY_CONTENT;
import static com.ixortalk.authserver.web.rest.UserResourceProfilePictureIntTest.PHOTO_CONTENT_TYPE;
import static com.ixortalk.test.oauth2.OAuth2EmbeddedTestServer.CLIENT_ID_USER;
import static com.ixortalk.test.oauth2.OAuth2TestTokens.adminToken;
import static com.ixortalk.test.oauth2.OAuth2TestTokens.userToken;
import static com.jayway.restassured.RestAssured.given;
import static java.net.HttpURLConnection.*;
import static java.util.UUID.randomUUID;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.restdocs.restassured.RestAssuredRestDocumentation.document;

@TestPropertySource(properties = {"com.ixortalk.s3.default-bucket: default-bucket-test-config"})
public class UserResourceProfilePictureRestDocTest extends AbstractRestDocTest {

    public static final String INITIAL_PROFILE_PICTURE_KEY = randomUUID().toString();

    public static final String MULTIPART_PART_NAME = "file";

    @MockBean
    protected AwsS3Template awsS3Template;

    @Inject
    private UserRepository userRepository;

    private User user;

    @Before
    public void before() {
        user = userRepository.save(aUser().withProfilePictureKey(INITIAL_PROFILE_PICTURE_KEY).build());
    }

    @After
    public void after() {
        userRepository.delete(user);
        userRepository.findOneByLogin(CLIENT_ID_USER).ifPresent(user -> userRepository.delete(user));
    }

    @Test
    public void getProfilePictureByLogin() {
        mockGetFromS3(awsS3Template, user.getProfilePictureKey(), BINARY_CONTENT, PHOTO_CONTENT_TYPE);

        given(this.spec)
            .auth().preemptive().oauth2(adminToken().getValue())
            .filter(
                document("get-profile-picture-by-login/ok",
                    preprocessRequest(staticUris(), prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestHeaders(AUTHORIZATION_TOKEN_HEADER),
                    pathParameters(
                        parameterWithName("login").description("The login for the user to retrieve the profile picture from.")
                    )
                )
            )
            .when()
            .get("/api/users/{login}/profile-picture", user.getLogin())
            .then()
            .statusCode(HTTP_OK);
    }

    @Test
    public void getProfilePictureByLogin_notFound() {
        given(this.spec)
            .auth().preemptive().oauth2(adminToken().getValue())
            .filter(
                document("get-profile-picture-by-login/not-found",
                    preprocessRequest(staticUris(), prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestHeaders(AUTHORIZATION_TOKEN_HEADER),
                    pathParameters(
                        parameterWithName("login").description("The login for the user to retrieve the profile picture from.")
                    )
                )
            )
            .when()
            .get("/api/users/{login}/profile-picture", user.getLogin())
            .then()
            .statusCode(HTTP_NOT_FOUND);
    }

    @Test
    public void getProfilePictureByKey() {
        mockGetFromS3(awsS3Template, user.getProfilePictureKey(), BINARY_CONTENT, PHOTO_CONTENT_TYPE);

        given(this.spec)
            .auth().preemptive().oauth2(adminToken().getValue())
            .filter(
                document("get-profile-picture-by-key/ok",
                    preprocessRequest(staticUris(), prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestHeaders(AUTHORIZATION_TOKEN_HEADER),
                    pathParameters(
                        parameterWithName("profilePictureKey").description("The key for the profile picture, this key is unique for every uploaded profile picture, can be retrieved via the me call (`/user`).")
                    )
                )
            )
            .when()
            .get("/api/profile-pictures/{profilePictureKey}", INITIAL_PROFILE_PICTURE_KEY)
            .then()
            .statusCode(HTTP_OK);
    }

    @Test
    public void getProfilePictureByKey_notFound() {
        given(this.spec)
            .auth().preemptive().oauth2(adminToken().getValue())
            .filter(
                document("get-profile-picture-by-key/not-found",
                    preprocessRequest(staticUris(), prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestHeaders(AUTHORIZATION_TOKEN_HEADER),
                    pathParameters(
                        parameterWithName("profilePictureKey").description("The key for the profile picture, this key is unique for every uploaded profile picture, can be retrieved via the me call (`/user`).")
                    )
                )
            )
            .when()
            .get("/api/profile-pictures/{profilePictureKey}", "nonExisting")
            .then()
            .statusCode(HTTP_NOT_FOUND);
    }

    @Test
    public void setProfilePicture_asUser_WithAccess() throws IOException {
        User user = userRepository.save(aUser().withLogin(CLIENT_ID_USER).build());

        mockPutInS3OnlyExpectingBytes(awsS3Template, BINARY_CONTENT);

        given(this.spec)
            .auth().preemptive().oauth2(userToken().getValue())
            .filter(
                document("set-profile-picture/ok",
                    preprocessRequest(staticUris(), prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestHeaders(AUTHORIZATION_TOKEN_HEADER),
                    pathParameters(
                        parameterWithName("login").description("The login for the user to set the profile picture for.")
                    ),
                    requestParts(partWithName(MULTIPART_PART_NAME).description("The binary picture content"))
                )
            )
            .contentType(MULTIPART_FORM_DATA_VALUE)
            .multiPart(MULTIPART_PART_NAME, MULTIPART_PART_NAME, BINARY_CONTENT)
            .when()
            .post("/api/users/{login}/profile-picture", user.getLogin())
            .then()
            .statusCode(HTTP_OK);
    }

    @Test
    public void setProfilePicture_asUser_NoAccess() {
        given(this.spec)
            .auth().preemptive().oauth2(userToken().getValue())
            .filter(
                document("set-profile-picture/not-allowed",
                    preprocessRequest(staticUris(), prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestHeaders(AUTHORIZATION_TOKEN_HEADER),
                    pathParameters(
                        parameterWithName("login").description("The login for the user to set the profile picture for.")
                    ),
                    requestParts(partWithName(MULTIPART_PART_NAME).description("The binary picture content"))
                )
            )
            .contentType(MULTIPART_FORM_DATA_VALUE)
            .multiPart("file", "file", BINARY_CONTENT)
            .when()
            .post("/api/users/{login}/profile-picture", user.getLogin())
            .then()
            .statusCode(HTTP_FORBIDDEN);
    }
}
