package org.koitharu.kotatsu.secret

import android.text.InputFilter
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.koitharu.kotatsu.R

/** Asks for the PIN or fingerprint before the secret space opens; the first use sets the PIN up. */
object SecretGate {

	private const val MIN_PIN_LENGTH = 4
	private const val MAX_PIN_LENGTH = 16

	fun open(activity: FragmentActivity) {
		when {
			!SecretSpace.hasPin -> askNewPin(activity)
			SecretSpace.isBiometricEnabled && canUseBiometric(activity) -> askBiometric(activity)
			else -> askPin(activity)
		}
	}

	private fun askPin(activity: FragmentActivity) {
		showPinDialog(activity, R.string.secret_enter_pin) { pin ->
			if (SecretSpace.verifyPin(pin)) {
				offerBiometric(activity)
			} else {
				Toast.makeText(activity, R.string.secret_wrong_pin, Toast.LENGTH_SHORT).show()
			}
		}
	}

	private fun askNewPin(activity: FragmentActivity) {
		showPinDialog(activity, R.string.secret_set_pin) { pin ->
			if (pin.length < MIN_PIN_LENGTH) {
				Toast.makeText(activity, R.string.secret_pin_too_short, Toast.LENGTH_SHORT).show()
				return@showPinDialog
			}
			showPinDialog(activity, R.string.secret_confirm_pin) { repeated ->
				if (repeated == pin) {
					SecretSpace.setPin(pin)
					offerBiometric(activity)
				} else {
					Toast.makeText(activity, R.string.secret_pin_mismatch, Toast.LENGTH_SHORT).show()
				}
			}
		}
	}

	/** Offers fingerprint unlock once, then opens the space. */
	private fun offerBiometric(activity: FragmentActivity) {
		if (SecretSpace.isBiometricEnabled || SecretSpace.isBiometricOffered || !canUseBiometric(activity)) {
			SecretSpace.enter(activity)
			return
		}
		SecretSpace.isBiometricOffered = true
		MaterialAlertDialogBuilder(activity)
			.setMessage(R.string.secret_use_biometric)
			.setCancelable(false)
			.setPositiveButton(android.R.string.yes) { _, _ ->
				SecretSpace.isBiometricEnabled = true
				SecretSpace.enter(activity)
			}
			.setNegativeButton(android.R.string.no) { _, _ -> SecretSpace.enter(activity) }
			.show()
	}

	private fun askBiometric(activity: FragmentActivity) {
		val prompt = BiometricPrompt(
			activity,
			ContextCompat.getMainExecutor(activity),
			object : BiometricPrompt.AuthenticationCallback() {
				override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
					SecretSpace.enter(activity)
				}

				override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
					if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_CANCELED) {
						askPin(activity)
					}
				}
			},
		)
		prompt.authenticate(
			BiometricPrompt.PromptInfo.Builder()
				.setTitle(activity.getString(R.string.secret_enter_pin))
				.setAllowedAuthenticators(BIOMETRIC_WEAK)
				.setNegativeButtonText(activity.getString(R.string.secret_use_pin))
				.build(),
		)
	}

	private fun canUseBiometric(activity: FragmentActivity) =
		BiometricManager.from(activity).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

	private fun showPinDialog(activity: FragmentActivity, titleRes: Int, onResult: (String) -> Unit) {
		val input = EditText(activity).apply {
			inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
			filters = arrayOf(InputFilter.LengthFilter(MAX_PIN_LENGTH))
			setSingleLine()
		}
		val padding = (24 * activity.resources.displayMetrics.density).toInt()
		val container = FrameLayout(activity).apply {
			setPadding(padding, padding / 3, padding, 0)
			addView(input, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
		}
		MaterialAlertDialogBuilder(activity)
			.setTitle(titleRes)
			.setView(container)
			.setPositiveButton(android.R.string.ok) { _, _ -> onResult(input.text.toString()) }
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}
}
