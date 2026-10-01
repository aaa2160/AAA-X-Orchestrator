package com.aaa.orchestrator.engine

import android.webkit.JavascriptInterface
import android.webkit.WebView
import timber.log.Timber

/**
 * Robust DOM Automation Engine for Twitter/X account creation.
 * Executes intelligent selector matching, human-like typing simulation,
 * and security challenge detection (Face Verification / Arkose Captcha).
 */
class TwitterAutoPilot(
    private val onFaceVerificationDetected: () -> Unit,
    private val onStepChanged: (String) -> Unit,
    private val onAccountCompleted: (String) -> Unit
) {

    @JavascriptInterface
    fun reportFaceVerification() {
        Timber.w("JavaScript reported Face Verification / Security Challenge detected!")
        onFaceVerificationDetected()
    }

    @JavascriptInterface
    fun reportStep(stepName: String) {
        Timber.i("Twitter AutoPilot Step: $stepName")
        onStepChanged(stepName)
    }

    @JavascriptInterface
    fun reportCompletion(cookieData: String) {
        Timber.i("Twitter AutoPilot completed registration successfully!")
        onAccountCompleted(cookieData)
    }

    companion object {
        fun buildAutoPilotScript(
            name: String,
            phone: String,
            birthMonth: Int,
            birthDay: Int,
            birthYear: Int,
            password: String,
            otp: String? = null
        ): String {
            val safeName = name.replace("'", "\\'")
            val safePhone = phone.replace("'", "\\'")
            val safePassword = password.replace("'", "\\'")
            val safeOtp = otp?.replace("'", "\\'") ?: ""

            return """
            (function() {
                var url = window.location.href;

                // 1. Check for Face Verification / Arkose Challenge
                var pageText = document.body ? document.body.innerText : '';
                var isFaceChallenge = url.indexOf('challenge') !== -1 ||
                    url.indexOf('arkose') !== -1 ||
                    url.indexOf('authenticate') !== -1 ||
                    url.indexOf('identity') !== -1 ||
                    url.indexOf('access') !== -1 ||
                    pageText.indexOf('Verify your identity') !== -1 ||
                    pageText.indexOf('Take a selfie') !== -1 ||
                    pageText.indexOf('face verification') !== -1 ||
                    pageText.indexOf('Authenticate your account') !== -1 ||
                    pageText.indexOf("Let's make sure you're human") !== -1 ||
                    pageText.indexOf('Prove you are human') !== -1;

                if (isFaceChallenge) {
                    if (window.AndroidBridge && window.AndroidBridge.reportFaceVerification) {
                        window.AndroidBridge.reportFaceVerification();
                    }
                    return 'face_verification_detected';
                }

                // Helper to simulate user typing & change events
                function fillInput(el, val) {
                    if (!el) return false;
                    el.focus();
                    el.value = val;
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                    return true;
                }

                // Helper to find and click button by text
                function clickButtonByText(candidates) {
                    var buttons = document.querySelectorAll('button, div[role="button"]');
                    for (var i = 0; i < buttons.length; i++) {
                        var b = buttons[i];
                        var text = (b.innerText || b.textContent || '').trim().toLowerCase();
                        for (var j = 0; j < candidates.length; j++) {
                            if (text === candidates[j].toLowerCase()) {
                                b.click();
                                return true;
                            }
                        }
                    }
                    return false;
                }

                // 2. Step 1: Initial Signup Form (Name, Phone, DOB)
                if (url.indexOf('signup') !== -1 || url.indexOf('flow') !== -1) {
                    // Check if 'Use phone instead' button exists
                    var usePhoneBtn = document.querySelectorAll('span, div[role="button"]');
                    for (var i = 0; i < usePhoneBtn.length; i++) {
                        if (usePhoneBtn[i].innerText && usePhoneBtn[i].innerText.indexOf('Use phone instead') !== -1) {
                            usePhoneBtn[i].click();
                            break;
                        }
                    }

                    // Fill Name
                    var nameInput = document.querySelector('input[name="name"], input[autocomplete="name"]');
                    if (nameInput && !nameInput.value) {
                        fillInput(nameInput, '$safeName');
                        if (window.AndroidBridge) window.AndroidBridge.reportStep('Filled Name');
                    }

                    // Fill Phone
                    var phoneInput = document.querySelector('input[name="phone_number"], input[type="tel"]');
                    if (phoneInput && !phoneInput.value) {
                        fillInput(phoneInput, '$safePhone');
                        if (window.AndroidBridge) window.AndroidBridge.reportStep('Filled Phone');
                    }

                    // Fill Date of Birth selects
                    var selects = document.querySelectorAll('select');
                    if (selects.length >= 3) {
                        // Month
                        if (!selects[0].value || selects[0].value === '0') {
                            selects[0].value = '$birthMonth';
                            selects[0].dispatchEvent(new Event('change', { bubbles: true }));
                        }
                        // Day
                        if (!selects[1].value || selects[1].value === '0') {
                            selects[1].value = '$birthDay';
                            selects[1].dispatchEvent(new Event('change', { bubbles: true }));
                        }
                        // Year
                        if (!selects[2].value || selects[2].value === '0') {
                            selects[2].value = '$birthYear';
                            selects[2].dispatchEvent(new Event('change', { bubbles: true }));
                        }
                    }

                    // 3. Step 2 & 3: Next & Sign Up Buttons
                    var nextClicked = clickButtonByText(['Next', 'Sign up', 'Sign Up']);
                    if (nextClicked && window.AndroidBridge) {
                        window.AndroidBridge.reportStep('Clicked Next/Sign Up');
                    }
                }

                // 4. Step 4: OTP Verification Screen
                if ('$safeOtp' !== '' && (pageText.indexOf('We sent you a code') !== -1 || pageText.indexOf('verification code') !== -1)) {
                    var otpInput = document.querySelector('input[name="verfication_code"], input[autocomplete="one-time-code"], input[name="code"], input[data-testid="ocfEnterTextTextInput"]');
                    if (otpInput && !otpInput.value) {
                        fillInput(otpInput, '$safeOtp');
                        if (window.AndroidBridge) window.AndroidBridge.reportStep('Filled OTP Code');
                        setTimeout(function() {
                            clickButtonByText(['Next', 'Verify']);
                        }, 500);
                    }
                }

                // 5. Step 5: Password Screen
                if (pageText.indexOf("You'll need a password") !== -1 || pageText.indexOf('Enter a password') !== -1) {
                    var passInput = document.querySelector('input[name="password"], input[type="password"]');
                    if (passInput && !passInput.value) {
                        fillInput(passInput, '$safePassword');
                        if (window.AndroidBridge) window.AndroidBridge.reportStep('Filled Password');
                        setTimeout(function() {
                            clickButtonByText(['Next', 'Sign up']);
                        }, 500);
                    }
                }

                // 6. Step 6: Post-Signup Skips (Profile Picture, Bio, etc.)
                if (pageText.indexOf('Pick a profile picture') !== -1 || pageText.indexOf('What should we call you') !== -1 || pageText.indexOf('Turn on notifications') !== -1) {
                    clickButtonByText(['Skip for now', 'Not now', 'Skip']);
                }

                return 'autopilot_executed';
            })();
            """.trimIndent()
        }
    }
}
