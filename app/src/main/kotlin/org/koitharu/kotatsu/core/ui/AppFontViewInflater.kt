package org.koitharu.kotatsu.core.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatCheckedTextView
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.AppCompatToggleButton
import com.google.android.material.theme.MaterialComponentsViewInflater

/**
 * Set as the theme's `viewInflaterClass`, so every layout inflated through AppCompat (screens,
 * list items, dialogs, sheets, popup menus) gets the font the user picked. It does nothing while
 * the default font is in use.
 */
class AppFontViewInflater : MaterialComponentsViewInflater() {

	private fun <T : TextView> T.withFont(): T {
		AppFont.apply(this)
		return this
	}

	override fun createTextView(context: Context, attrs: AttributeSet): AppCompatTextView =
		super.createTextView(context, attrs).withFont()

	override fun createButton(context: Context, attrs: AttributeSet): AppCompatButton =
		super.createButton(context, attrs).withFont()

	override fun createEditText(context: Context, attrs: AttributeSet): AppCompatEditText =
		super.createEditText(context, attrs).withFont()

	override fun createCheckBox(context: Context, attrs: AttributeSet): AppCompatCheckBox =
		super.createCheckBox(context, attrs).withFont()

	override fun createRadioButton(context: Context, attrs: AttributeSet): AppCompatRadioButton =
		super.createRadioButton(context, attrs).withFont()

	override fun createCheckedTextView(context: Context, attrs: AttributeSet): AppCompatCheckedTextView =
		super.createCheckedTextView(context, attrs).withFont()

	override fun createAutoCompleteTextView(context: Context, attrs: AttributeSet): AppCompatAutoCompleteTextView =
		super.createAutoCompleteTextView(context, attrs).withFont()

	override fun createMultiAutoCompleteTextView(
		context: Context,
		attrs: AttributeSet,
	): AppCompatMultiAutoCompleteTextView = super.createMultiAutoCompleteTextView(context, attrs).withFont()

	override fun createToggleButton(context: Context, attrs: AttributeSet): AppCompatToggleButton =
		super.createToggleButton(context, attrs).withFont()
}
