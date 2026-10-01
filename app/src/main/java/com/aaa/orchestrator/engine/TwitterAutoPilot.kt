package com.aaa.orchestrator.engine

import android.webkit.JavascriptInterface
import android.webkit.WebView
import timber.log.Timber

/**
 * Enterprise DOM Automation Engine for Twitter/X account creation.
 * Executes resilient React 18 synthetic input injection, value-tracker bypassing,
 * single-page application (SPA) dynamic step progression,
 * and security challenge detection (KYC Selfie / Arkose Captcha).
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
                var lastChallengeReportTime = 0;

                function simulateClick(el) {
                    if (!el) return false;
                    try { el.focus(); } catch(e) {}
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
                    try { el.click(); } catch(e) {}
                    return true;
                }

                function triggerNativeInput(el, val) {
                    if (!el || !val) return false;
                    try {
                        el.focus();
                        if (el._valueTracker) {
                            el._valueTracker.setValue('');
                        }
                        var proto = (el.tagName && el.tagName.toLowerCase() === 'textarea') ?
                            window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
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
                        el.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, key: 'Enter' }));
                        el.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true, key: 'Enter' }));
                        return true;
                    } catch(err) {
                        return false;
                    }
                }

                function triggerSelect(sel, val, isMonth) {
                    if (!sel || !sel.tagName || sel.tagName.toLowerCase() !== 'select') return false;
                    try {
                        sel.focus();
                        var strVal = String(val);
                        var intVal = parseInt(val, 10);
                        var months = ["january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december"];
                        var targetMonth = (isMonth && intVal >= 1 && intVal <= 12) ? months[intVal - 1] : null;

                        var matchedIndex = -1;
                        if (sel.options && sel.options.length > 0) {
                            for (var i = 0; i < sel.options.length; i++) {
                                var opt = sel.options[i];
                                var optVal = (opt.value || '').trim();
                                var optText = (opt.text || opt.innerText || '').trim().toLowerCase();
                                if (optVal === strVal || parseInt(optVal, 10) === intVal) {
                                    matchedIndex = i;
                                    break;
                                }
                                if (targetMonth && (optText === targetMonth || optText.indexOf(targetMonth) !== -1)) {
                                    matchedIndex = i;
                                    break;
                                }
                                if (!isMonth && optText === strVal) {
                                    matchedIndex = i;
                                    break;
                                }
                            }
                        }
                        if (matchedIndex === -1 && intVal > 0 && intVal < sel.options.length) {
                            matchedIndex = intVal;
                        }

                        if (matchedIndex !== -1) {
                            sel.selectedIndex = matchedIndex;
                            var chosenOpt = sel.options[matchedIndex];
                            chosenOpt.selected = true;

                            if (sel._valueTracker) {
                                sel._valueTracker.setValue('');
                            }
                            var proto = window.HTMLSelectElement.prototype;
                            var descriptor = Object.getOwnPropertyDescriptor(proto, 'value');
                            if (descriptor && descriptor.set) {
                                descriptor.set.call(sel, chosenOpt.value);
                            } else {
                                sel.value = chosenOpt.value;
                            }
                            sel.dispatchEvent(new Event('input', { bubbles: true, cancelable: true }));
                            sel.dispatchEvent(new Event('change', { bubbles: true, cancelable: true }));
                            return true;
                        }
                        return false;
                    } catch(e) {
                        return false;
                    }
                }

                function clickButtonByText(candidates, exact) {
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
                        var isDisabled = testIdBtn.getAttribute('aria-disabled') === 'true' || testIdBtn.disabled === true || testIdBtn.hasAttribute('disabled');
                        if (!isDisabled) {
                            simulateClick(testIdBtn);
                            return true;
                        }
                    }
                    return clickButtonByText(['Next', 'Sign up', 'Sign Up'], true);
                }

                window._checkAutoPilot = function(force) {
                    var now = Date.now();
                    if (!force && (now - lastActionTime < 800)) return;
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
                        clickButtonByText(['Authenticate', 'Start challenge', 'Verify'], true);
                        if (now - lastChallengeReportTime > 15000) {
                            lastChallengeReportTime = now;
                            if (window.AndroidBridge && window.AndroidBridge.reportFaceVerification) {
                                window.AndroidBridge.reportFaceVerification();
                            }
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
                            lastActionTime = now + 1000;
                            return;
                        }
                    }

                    // 4. Step 1: Switch to Phone if Twitter defaulted to Email
                    var emailInput = document.querySelector('input[type="email"], input[name="email"], input[autocomplete="email"]');
                    var phoneInput = document.querySelector('input[name="phone_number"], input[name="phone"], input[autocomplete="tel"], input[type="tel"], input[data-testid*="phone"]');

                    if (emailInput && !phoneInput) {
                        var usePhoneBtn = document.querySelector('[data-testid="ocfSignupEmailPhoneToggle"]');
                        if (usePhoneBtn) {
                            simulateClick(usePhoneBtn);
                        } else {
                            clickButtonByText(['Use phone instead', 'Use phone'], false);
                        }
                        if (window.AndroidBridge) window.AndroidBridge.reportStep('Switched to Phone input');
                        lastActionTime = now + 800;
                        return;
                    }

                    // 5. Step 1: Fill Name, Phone, and DOB
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

                    // Fill DOB Selects specifically targeting SELECT tags
                    var allSelects = Array.from(document.querySelectorAll('select'));
                    var monthSel = document.querySelector('#SELECTOR_1, select[aria-label*="Month" i], select[name*="month" i]') || allSelects[0];
                    var daySel = document.querySelector('#SELECTOR_2, select[aria-label*="Day" i], select[name*="day" i]') || allSelects[1];
                    var yearSel = document.querySelector('#SELECTOR_3, select[aria-label*="Year" i], select[name*="year" i]') || allSelects[2];

                    if (monthSel && (!monthSel.value || monthSel.value === '0' || monthSel.value === '')) {
                        triggerSelect(monthSel, window._apConfig.birthMonth, true);
                    }
                    if (daySel && (!daySel.value || daySel.value === '0' || daySel.value === '')) {
                        triggerSelect(daySel, window._apConfig.birthDay, false);
                    }
                    if (yearSel && (!yearSel.value || yearSel.value === '0' || yearSel.value === '')) {
                        triggerSelect(yearSel, window._apConfig.birthYear, false);
                    }

                    // Click Next on signup step once fields are populated
                    if (nameInput && phoneInput && nameInput.value && phoneInput.value && (force || lastStep !== 'signup_next')) {
                        var nextBtn = document.querySelector('[data-testid="ocfSignupNextLink"], [data-testid="SignupButton"], [data-testid="nextButton"]');
                        if (nextBtn) {
                            var isDisabled = nextBtn.getAttribute('aria-disabled') === 'true' || nextBtn.disabled === true || nextBtn.hasAttribute('disabled');
                            if (!isDisabled) {
                                simulateClick(nextBtn);
                                lastStep = 'signup_next';
                                lastActionTime = now + 1200;
                                if (window.AndroidBridge) window.AndroidBridge.reportStep('Submitted Step 1');
                                return;
                            }
                        }
                    }

                    // 6. Step 2 & 3: Customize experience & Review screens
                    if (pageText.indexOf('Customize your experience') !== -1) {
                        var customNext = clickNextButton();
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
                    if (pageText.indexOf('Verify phone') !== -1 || pageText.indexOf('text your verification code') !== -1) {
                        var confirmBtn = document.querySelector('[data-testid="confirmationSheetConfirm"]');
                        if (confirmBtn) {
                            simulateClick(confirmBtn);
                            if (window.AndroidBridge) window.AndroidBridge.reportStep('Confirmed Verify Phone modal');
                            lastActionTime = now + 1000;
                            return;
                        }
                        var okClicked = clickButtonByText(['OK', 'Verify', 'Confirm'], true);
                        if (okClicked) {
                            if (window.AndroidBridge) window.AndroidBridge.reportStep('Confirmed Verify Phone dialog');
                            lastActionTime = now + 1000;
                            return;
                        }
                    }

                    // 8. Step 4: OTP Verification Screen
                    var isOtpScreen = pageText.indexOf('We sent you a code') !== -1 || pageText.indexOf('verification code') !== -1 || url.indexOf('enter_code') !== -1;
                    if (isOtpScreen) {
                        var otpInput = document.querySelector('input[name="verfication_code"], input[name="verification_code"], input[autocomplete="one-time-code"], input[name="code"], input[inputmode="numeric"], input[data-testid="ocfEnterTextTextInput"]');
                        if (otpInput) {
                            if ((!otpInput.value || otpInput.value.length === 0) && window._apConfig.otp) {
                                triggerNativeInput(otpInput, window._apConfig.otp);
                                if (window.AndroidBridge) window.AndroidBridge.reportStep('Filled OTP: ' + window._apConfig.otp);
                                lastActionTime = now + 800;
                                setTimeout(function() {
                                    clickNextButton();
                                }, 500);
                                return;
                            } else if (otpInput.value && otpInput.value.length >= 6) {
                                clickNextButton();
                                lastActionTime = now + 1000;
                                return;
                            }
                        }
                    }

                    // 9. Step 5: Password Screen
                    var isPassScreen = pageText.indexOf("You'll need a password") !== -1 || pageText.indexOf('Enter a password') !== -1 || (pageText.indexOf('password') !== -1 && document.querySelector('input[type="password"]'));
                    if (isPassScreen) {
                        var passInput = document.querySelector('input[name="password"], input[type="password"], input[autocomplete="new-password"]');
                        if (passInput) {
                            if ((!passInput.value || passInput.value.length === 0) && window._apConfig.password) {
                                triggerNativeInput(passInput, window._apConfig.password);
                                if (window.AndroidBridge) window.AndroidBridge.reportStep('Filled Password');
                                lastActionTime = now + 800;
                                setTimeout(function() {
                                    clickNextButton();
                                }, 500);
                                return;
                            } else if (passInput.value && passInput.value.length >= 8) {
                                clickNextButton();
                                lastActionTime = now + 1000;
                                return;
                            }
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

                // Run immediately and setup continuous interval & mutation observer
                window._checkAutoPilot(true);
                setInterval(function() { window._checkAutoPilot(false); }, 750);

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
