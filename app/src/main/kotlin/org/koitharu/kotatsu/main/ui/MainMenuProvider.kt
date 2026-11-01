package org.koitharu.kotatsu.main.ui

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.secret.SecretSpace

class MainMenuProvider(
	private val viewModel: MainViewModel,
	private val onLeaveSecret: () -> Unit,
) : MenuProvider {

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.opt_main, menu)
	}

	override fun onPrepareMenu(menu: Menu) {
		super.onPrepareMenu(menu)
		menu.findItem(R.id.action_incognito)?.isChecked = viewModel.isIncognitoModeEnabled.value
		menu.findItem(R.id.action_leave_secret)?.isVisible = SecretSpace.isActive
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
		R.id.action_incognito -> {
			viewModel.setIncognitoMode(!menuItem.isChecked)
			true
		}
		R.id.action_leave_secret -> {
			onLeaveSecret()
			true
		}

		else -> false
	}
}
