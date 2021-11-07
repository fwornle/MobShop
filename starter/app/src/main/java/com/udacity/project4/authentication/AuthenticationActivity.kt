package com.udacity.project4.authentication

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.udacity.project4.R
import com.udacity.project4.databinding.ActivityAuthenticationBinding
import com.udacity.project4.locationreminders.RemindersActivity
import timber.log.Timber
import com.google.firebase.auth.FirebaseAuth





/**
 * This class should be the starting point of the app, It asks the users to sign in / register, and redirects the
 * signed in users to the RemindersActivity.
 */
class AuthenticationActivity : AppCompatActivity() {

    // bind views
    private lateinit var binding: ActivityAuthenticationBinding

    // initialize activity
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate the layout
        binding = DataBindingUtil.setContentView(this, R.layout.activity_authentication)
        setContentView(binding.root)

        // use FirebaseUI auth flow to sign in users
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {

            // already authenticated --> enter app
            sendAuthUserToMainApp(auth)

        } else {

            // not yet logged in --> activate Login button

            // install onClickListener for "Login" button
            binding.authButton.setOnClickListener { launchSignInFlow() }

        }

    }

    // firebaseUI auth flow
    private fun launchSignInFlow() {
        // firebaseUI: Choose authentication providers
        // https://github.com/firebase/FirebaseUI-Android/blob/master/auth/README.md#custom-layout
        val providers = arrayListOf(
            AuthUI.IdpConfig.EmailBuilder().build(),
            AuthUI.IdpConfig.GoogleBuilder().build(),
        )

        // firebaseUI: create sign-in intent
        val signInIntent = AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(providers)
            //.setIsSmartLockEnabled(!BuildConfig.DEBUG /* credentials */, true /* hints */)
            .setTheme(R.style.AppTheme)
            .build()

        // firebaseUI: launch sign-in intent
        signInLauncher.launch(signInIntent)
    }

    // redirect successfully authenticated users to the main app
    private fun sendAuthUserToMainApp(auth: FirebaseAuth) {
        val user = auth.currentUser
        var isNewUser: Boolean? = null

        // user object returned?
        user?.let {
            // yup - extract meta information
            isNewUser = it.metadata?.creationTimestamp == it.metadata?.lastSignInTimestamp
        }

        // send user to "RemindersActivity"
        val intent = Intent(applicationContext, RemindersActivity::class.java)
        startActivity(
            intent
                .putExtra("userName", user?.displayName)
                .putExtra("isNewUser", isNewUser)
        )

        // this activity is done
        finish()

    }

    // firebaseUI auth flow - register callback 'onSignInResult'
    private val signInLauncher = registerForActivityResult(
        FirebaseAuthUIActivityResultContract()
    ) { res ->
        this.onSignInResult(res)
    }

    // callback - will be called by the FirebaseUI auth flow upon sign-in
    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {

        val response = result.idpResponse

        // check result code of returning firebaseUI auth flow
        if (result.resultCode == RESULT_OK) {

            // Successfully signed in
            val auth = FirebaseAuth.getInstance()
            sendAuthUserToMainApp(auth)

        } else {

            // Sign in failed
            // If response is null the user canceled the sign-in flow using the back button
            response?.let {
                // ...otherwise --> some error occurred
                Timber.i("Auth Error: ${it.error?.errorCode}")
            } ?: Timber.i("User cancelled sign-in flow.")

        }  // else: unsuccessful firebaseUI flow

    }  // onSignInResult

}
