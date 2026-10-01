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
                window._apConfig = {
                    name: '$safeName',
                    phone: '$safePhone',
                    birthMonth: $birthMonth,
                    birthDay: $birthDay,
                    birthYear: $birthYear,
                    password: '$safePassword',
                    otp: '$safeOtp'
                };

                if (window._twitterAutoPilotActive) {
                    if (typeof window._checkAutoPilot === 'function') {
                        window._checkAutoPilot(true);
                    }
                    return 'config_updated_and_executed';
                }
                window._twitterAutoPilotActive = true;

                var lastStep = '';
                var lastActionTime = 0;

                function simulateClick(el) {
                    if (!el) return false;
                    try {
                        el.focus();
                    } catch(e) {}
                    try {
                        var rect = el.getBoundingClientRect();
                        var cx = rect.left + rect.width / 2;
                        var cy = rect.top + rect.height / 2;
                        var opts = { bubbles: true, cancelable: true, view: window, clientX: cx, clientY: cy };
                        el.dispatchEvent(new PointerEvent('pointerdown', opts));
                        el.dispatchEvent(new MouseEvent('mousedown', opts));
                        el.dispatchEvent(new PointerEvent('pointerup', opts));
                        el.dispatchEvent(new MouseEvent('mouseup', opts));
                        el.dispatchEvent(new MouseEvent('click', opts));
                    } catch(e) {}
                    try {
                        el.click();
                    } catch(e) {}
                    return true;
                }

                function triggerNativeInput(el, val) {
                    if (!el || !val) return false;
                    try {
                        el.focus();
                        var proto = window.HTMLInputElement.prototype;
                        var descriptor = Object.getOwnPropertyDescriptor(proto, 'value');
                        if (descriptor && descriptor.set) {
                            descriptor.set.call(el, val);
                        } else {
                            el.value = val;
                        }
                        el.dispatchEvent(new Event('input', { bubbles: true, cancelable: true }));
                        el.dispatchEvent(new Event('change', { bubbles: true, cancelable: true }));
                        try {
                            el.dispatchEvent(new InputEvent('input', { bubbles: true, cancelable: true, inputType: 'insertText', data: val }));
                        } catch(ie) {}
                        el.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true }));
                        el.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true }));
                        el.blur();
                        return true;
                    } catch(err) {
                        return false;
                    }
                }

                function triggerSelect(sel, val) {
                    if (!sel) return false;
                    try {
                        sel.focus();
                        var strVal = String(val);
                        var descriptor = Object.getOwnPropertyDescriptor(window.HTMLSelectElement.prototype, 'value');
                        if (descriptor && descriptor.set) {
                            descriptor.set.call(sel, strVal);
                        } else {
                            sel.value = strVal;
                        }
                        sel.dispatchEvent(new Event('input', { bubbles: true, cancelable: true }));
                        sel.dispatchEvent(new Event('change', { bubbles: true, cancelable: true }));
                        sel.blur();
                        return true;
                    } catch(e) {
                        return false;
                    }
                }

                function clickButtonByText(candidates, exact) {
                    // 1. Search buttons, links, div[role=button]
                    var targets = document.querySelectorAll('button, div[role="button"], a[role="button"], a');
                    for (var i = 0; i < targets.length; i++) {
                        var el = targets[i];
                        var text = (el.innerText || el.textContent || '').trim().toLowerCase();
                        for (var j = 0; j < candidates.length; j++) {
                            var cand = candidates[j].toLowerCase();
                            if (exact ? (text === cand) : (text === cand || (text.indexOf(cand) !== -1 && text.length < cand.length + 35))) {
                                simulateClick(el);
                                return true;
                            }
                        }
                    }

                    // 2. Search inner spans/divs directly and click closest clickable ancestor
                    var spans = document.querySelectorAll('span, div');
                    for (var i = 0; i < spans.length; i++) {
                        var el = spans[i];
                        if (el.children.length > 2) continue;
                        var text = (el.innerText || el.textContent || '').trim().toLowerCase();
                        for (var j = 0; j < candidates.length; j++) {
                            var cand = candidates[j].toLowerCase();
                            if (text === cand || (text.indexOf(cand) !== -1 && text.length < cand.length + 25)) {
                                var parentClickable = el.closest('button, div[role="button"], a[role="button"], a') || el;
                                simulateClick(parentClickable);
                                return true;
                            }
                        }
                    }
                    return false;
                }

                function clickNextButton() {
                    var testIdBtn = document.querySelector('[data-testid="ocfSignupNextLink"], [data-testid="SignupButton"], [data-testid="nextButton"], [data-testid="ConfirmationSheetDoneButton"]');
                    if (testIdBtn) {
                        simulateClick(testIdBtn);
                        return true;
                    }
                    return clickButtonByText(['Next', 'Sign up', 'Sign Up'], true);
                }

                window._checkAutoPilot = function(force) {
                    var now = Date.now();
                    if (!force && (now - lastActionTime < 1000)) return;
                    if (force) {
                        lastActionTime = 0;
                        lastStep = '';
                    }

                    var url = window.location.href;
                    var pageText = document.body ? document.body.innerText : '';

                    // 1. Session Completion Check (Cookie & Timeline)
                    if (document.cookie && document.cookie.indexOf('auth_token') !== -1) {
                        if (window.AndroidBridge && window.AndroidBridge.reportCompletion) {
                            window.AndroidBridge.reportCompletion(document.cookie);
                            return;
                        }
                    }

                    // 2. Challenge / Face Verification / Arkose Check
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
                        return;
                    }

                    // 3. Step 0: Landing Screen Selection ("See what's happening" / "Join X today")
                    var hasLandingPrompt = pageText.indexOf("See what's happening") !== -1 ||
                                          pageText.indexOf('Join today') !== -1 ||
                                          pageText.indexOf('Happening now') !== -1 ||
                                          url.indexOf('signup') !== -1;

                    var nameInput = document.querySelector('input[name="name"], input[autocomplete="name"], input[data-testid*="name"]');
                    if (hasLandingPrompt && !nameInput) {
                        var clickedLanding = clickButtonByText([
                            'Continue with phone',
                            'Sign up with phone',
                            'Sign up with phone or email',
                            'Create account'
                        ], false);
                        if (clickedLanding) {
                            if (window.AndroidBridge) window.AndroidBridge.reportStep('Clicked Continue with phone');
                            lastActionTime = now + 1200;
                            return;
                        }
                    }

                    // 4. Step 1: Switch to Phone if Twitter defaulted to Email
                    var usePhoneBtn = clickButtonByText(['Use phone instead'], false);
                    if (usePhoneBtn) {
                        if (window.AndroidBridge) window.AndroidBridge.reportStep('Switched to Phone input');
                        lastActionTime = now + 800;
                        return;
                    }

                    // 5. Step 1: Fill Name, Phone, and DOB
                    var phoneInput = document.querySelector('input[name="phone_number"], input[autocomplete="tel"], input[type="tel"]');

                    if (nameInput && (!nameInput.value || nameInput.value.length === 0)) {
                        triggerNativeInput(nameInput, window._apConfig.name);
                        if (window.AndroidBridge) window.AndroidBridge.reportStep('Filled Name: ' + window._apConfig.name);
                        lastActionTime = now + 300;
                    }

                    if (phoneInput && (!phoneInput.value || phoneInput.value.length === 0)) {
                        triggerNativeInput(phoneInput, window._apConfig.phone);
                        if (window.AndroidBridge) window.AndroidBridge.reportStep('Filled Phone: ' + window._apConfig.phone);
                        lastActionTime = now + 300;
                    }

                    // Fill DOB Selects
                    var selects = document.querySelectorAll('select, div[data-testid*="select"], div[data-testid*="BirthDate"]');
                    if (selects.length >= 3) {
                        var monthSel = selects[0];
                        var daySel = selects[1];
                        var yearSel = selects[2];

                        if (!monthSel.value || monthSel.value === '0') {
                            triggerSelect(monthSel, window._apConfig.birthMonth);
                        }
                        if (!daySel.value || daySel.value === '0') {
                            triggerSelect(daySel, window._apConfig.birthDay);
                        }
                        if (!yearSel.value || yearSel.value === '0') {
                            triggerSelect(yearSel, window._apConfig.birthYear);
                        }
                    }

                    // Click Next on signup step once fields are populated
                    if (nameInput && phoneInput && nameInput.value && phoneInput.value && (force || lastStep !== 'signup_next')) {
                        var nextClicked = clickNextButton();
                        if (nextClicked) {
                            lastStep = 'signup_next';
                            lastActionTime = now + 1200;
                            if (window.AndroidBridge) window.AndroidBridge.reportStep('Submitted Step 1');
                            return;
                        }
                    }

                    // 6. Step 2 & 3: Customize experience & Review screens
                    if (pageText.indexOf('Customize your experience') !== -1) {
                        var customNext = clickButtonByText(['Next'], true);
                        if (customNext) {
                            lastActionTime = now + 1000;
                            return;
                        }
                    }

                    if (pageText.indexOf('Create your account') !== -1 && lastStep !== 'signup_clicked') {
                        var signClicked = clickButtonByText(['Sign up', 'Sign Up'], true);
                        if (signClicked) {
                            lastStep = 'signup_clicked';
                            lastActionTime = now + 1000;
                            if (window.AndroidBridge) window.AndroidBridge.reportStep('Clicked Sign up');
                            return;
                        }
                    }

                    // 7. Step 3.5: Phone Confirmation Dialog ("Verify phone")
                    // Twitter shows popup: "We'll text your verification code to... Standard SMS fees may apply."
                    if (pageText.indexOf('Verify phone') !== -1 || pageText.indexOf('text your verification code') !== -1) {
                        var okClicked = clickButtonByText(['OK', 'Verify'], true);
                        if (okClicked) {
                            if (window.AndroidBridge) window.AndroidBridge.reportStep('Confirmed Verify Phone dialog');
                            lastActionTime = now + 1000;
                            return;
                        }
                    }

                    // 8. Step 4: OTP Verification Screen
                    if (window._apConfig.otp && (pageText.indexOf('We sent you a code') !== -1 || pageText.indexOf('verification code') !== -1)) {
                        var otpInput = document.querySelector('input[name="verfication_code"], input[name="verification_code"], input[autocomplete="one-time-code"], input[name="code"], input[inputmode="numeric"], input[data-testid="ocfEnterTextTextInput"]');
                        if (otpInput && (!otpInput.value || otpInput.value.length === 0)) {
                            triggerNativeInput(otpInput, window._apConfig.otp);
                            if (window.AndroidBridge) window.AndroidBridge.reportStep('Filled OTP: ' + window._apConfig.otp);
                            lastActionTime = now + 800;
                            setTimeout(function() {
                                clickButtonByText(['Next', 'Verify'], true);
                            }, 500);
                            return;
                        }
                    }

                    // 9. Step 5: Password Screen
                    if (pageText.indexOf("You'll need a password") !== -1 || pageText.indexOf('Enter a password') !== -1) {
                        var passInput = document.querySelector('input[name="password"], input[type="password"]');
                        if (passInput && (!passInput.value || passInput.value.length === 0)) {
                            triggerNativeInput(passInput, window._apConfig.password);
                            if (window.AndroidBridge) window.AndroidBridge.reportStep('Filled Password');
                            lastActionTime = now + 800;
                            setTimeout(function() {
                                clickButtonByText(['Next', 'Sign up'], true);
                            }, 500);
                            return;
                        }
                    }

                    // 10. Post-Signup Onboarding Skips
                    if (pageText.indexOf('Pick a profile picture') !== -1 ||
                        pageText.indexOf('What should we call you') !== -1 ||
                        pageText.indexOf('Turn on notifications') !== -1 ||
                        pageText.indexOf('What do you want to see on X') !== -1 ||
                        pageText.indexOf('Follow 1 or more') !== -1) {
                        clickButtonByText(['Skip for now', 'Not now', 'Skip', 'Next'], true);
                        lastActionTime = now + 1000;
                    }
                }

                // Run immediately and setup interval observer
                window._checkAutoPilot(true);
                setInterval(function() { window._checkAutoPilot(false); }, 1000);

                var observer = new MutationObserver(function() {
                    window._checkAutoPilot(false);
                });
                observer.observe(document.body || document.documentElement, { childList: true, subtree: true });

                return 'autopilot_installed';
            })();
            """.trimIndent()
        }
    }
}
