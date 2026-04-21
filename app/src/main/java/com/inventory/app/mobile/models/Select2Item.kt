package com.inventory.app.mobile.models

class Select2Item(var id : Long, var text : String) {

    override fun toString(): String {
        return text // This determines what shows in the AutoCompleteTextView
    }
}