package de.joker.kloud.shared

@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This API is internal and only for use within approved packages."
)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class InternalApi
