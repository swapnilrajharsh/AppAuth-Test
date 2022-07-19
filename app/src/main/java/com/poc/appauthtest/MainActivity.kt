package com.poc.appauthtest

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import com.auth0.android.jwt.JWT
import com.poc.appauthtest.Utils.Constants
import net.openid.appauth.*
import net.openid.appauth.browser.BrowserAllowList
import net.openid.appauth.browser.VersionedBrowserMatcher
import java.security.MessageDigest
import java.security.SecureRandom

class MainActivity : AppCompatActivity() {

    private lateinit var signInButton : Button
    private lateinit var details : TextView
    private var authState = AuthState()
    private var jwt : JWT? = null
    private lateinit var authorizationService : AuthorizationService
    lateinit var authServiceConfig : AuthorizationServiceConfiguration

    companion object{
        val TAG = MainActivity::class.java.canonicalName
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initAuthServiceConfig()
        initAuthService()
    }

    private fun initAuthServiceConfig() {
        authServiceConfig = AuthorizationServiceConfiguration(
            Uri.parse(Constants.AUTH_URL),
            Uri.parse(Constants.TOKEN_URL),
            null,
            Uri.parse(Constants.URL_LOGOUT)
        )
    }

    private fun initAuthService() {
        val appAuthConfiguration = AppAuthConfiguration.Builder()
            .setBrowserMatcher(
                BrowserAllowList(
                    VersionedBrowserMatcher.CHROME_CUSTOM_TAB,
                    VersionedBrowserMatcher.SAMSUNG_CUSTOM_TAB
                )
            ).build()

        /*authorizationService = AuthorizationService(
            application,
            appAuthConfiguration
        )*/
        authorizationService = AuthorizationService(this)
    }

    private fun initViews() {
        signInButton = findViewById(R.id.sign_in_with_google)
        details = findViewById(R.id.details)
        signInButton.setOnClickListener { initiateOauthSignInFlow() }
    }

    private fun initiateOauthSignInFlow() {
        val secureRandom = SecureRandom()
        val bytes = ByteArray(64)
        secureRandom.nextBytes(bytes)

        val encoding = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        val codeVerifier = Base64.encodeToString(bytes, encoding)

        val digest = MessageDigest.getInstance(Constants.MESSAGE_DIGEST_ALGORITHM)
        val hash = digest.digest(codeVerifier.toByteArray())
        val codeChallenge = Base64.encodeToString(hash, encoding)

        val builder = AuthorizationRequest.Builder(
            authServiceConfig,
            Constants.CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(Constants.URL_AUTH_REDIRECT))
            .setCodeVerifier(codeVerifier,
                codeChallenge,
                Constants.CODE_VERIFIER_CHALLENGE_METHOD)

        builder.setScopes(Constants.SCOPE_PROFILE,
            Constants.SCOPE_EMAIL,
            Constants.SCOPE_OPENID,
            Constants.SCOPE_DRIVE)

        val request = builder.build()
        val intentBuilder = authorizationService.createCustomTabsIntentBuilder(request.toUri())
        val mAuthIntent : CustomTabsIntent = intentBuilder.build()
        val authIntent = authorizationService.getAuthorizationRequestIntent(request, mAuthIntent)
        launcher.launch(authIntent)
    }

    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult() ) {
            if (it.resultCode == Activity.RESULT_OK) {
                handleResponse(it.data!!)
            }
    }

    private fun handleResponse(result: Intent) {
        val authorizationResponse : AuthorizationResponse? = AuthorizationResponse.fromIntent(result)
        val error = AuthorizationException.fromIntent(result)

        authState = AuthState(authorizationResponse, error)
        val tokenExchangeRequest = authorizationResponse!!.createTokenExchangeRequest()
        authorizationService.performTokenRequest(tokenExchangeRequest) { response, exception ->
            if (exception != null) {
                authState = AuthState()
            } else {
                if (response != null) {
                    authState.update(response, exception)
                    jwt = JWT(response.idToken!!)
                    val detailsPerson = jwt.toString().split(".")

                    Log.d(TAG, "Values 1.Name : ${jwt!!.getClaim("name").asString()} " +
                            "Details : ${String(Base64.decode(detailsPerson[1], Base64.URL_SAFE))}")
                    details.text = "Values 1.Name : ${jwt!!.getClaim("name").asString()} " +
                            "\n2. Access Token : ${authState.accessToken} " +
                            "\n3. Decoded JWT : ${String(Base64.decode(detailsPerson[1], Base64.URL_SAFE))}"
                }
            }
        }
    }
}