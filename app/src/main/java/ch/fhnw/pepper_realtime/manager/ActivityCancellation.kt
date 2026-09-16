package ch.fhnw.pepper_realtime.manager

internal object ActivityCancellation {
    fun message(activity: String): String =
        "[UI event, not a user request] The user manually closed $activity on the tablet. " +
            "The activity is already closed by the app. You did not close it and must not claim that you did. " +
            "Address the user directly in the second person: acknowledge that THEY closed it, " +
            "then ask whether they would like to do something else. " +
            "Use the conversation language. For example in German: 'Du hast die Aktivitaet geschlossen. " +
            "Moechtest du etwas anderes machen?' Do not call a tool or restart the activity."
}
