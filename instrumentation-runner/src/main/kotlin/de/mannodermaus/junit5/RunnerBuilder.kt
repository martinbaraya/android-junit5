package de.mannodermaus.junit5

import android.util.Log
import org.junit.runner.Runner
import org.junit.runners.model.RunnerBuilder

/* Constants */

private const val LOG_TAG = "AndroidJUnit5"
private val jupiterTestAnnotations = listOf(
    "org.junit.jupiter.api.Test",
    "org.junit.jupiter.api.TestFactory",
    "org.junit.jupiter.params.ParameterizedTest")

/* Types */

/**
 * Custom RunnerBuilder hooked into the main Test Instrumentation Runner
 * provided by the Android Test Support Library, which allows to run
 * the JUnit Platform for instrumented tests. With this,
 * the default JUnit 4-based Runner for Android instrumented tests is,
 * in a way, tricked into detecting JUnit Jupiter tests as well.
 *
 * Applying the android-junit5 Gradle Plugin will hook this RunnerBuilder
 * into the instrumentation runner's lifecycle through a custom
 * "testInstrumentationRunnerArgument".
 *
 * (Suppressing unused, since this is hooked into the
 * project configuration via a Test Instrumentation Runner Argument.)
 */
@Suppress("unused")
class AndroidJUnit5Builder : RunnerBuilder() {

  private val junit5Available by lazy {
    try {
      // The verification order of this block is quite important.
      // Do not change it without thorough testing of potential consequences!
      // After tampering with this, verify that integration
      // with applications using JUnit 5 for UI tests still works,
      // AND that integration with applications NOT using JUnit 5 for UI tests still works.
      //
      // First, verify the existence of junit-jupiter-api on the classpath.
      // Then, verify that the JUnitPlatform Runner is available on the classpath.
      // It is VERY important to perform this check BEFORE verifying the existence
      // of the AndroidJUnit5 Runner, which inherits from JUnitPlatform. If this is omitted,
      // an uncatchable verification error will be raised, rendering instrumentation testing
      // for applications without the desire to include JUnit 5 effectively useless.
      // The simple Class.forName() check however will catch this allowed inconsistency,
      // and gracefully abort.
      Class.forName("org.junit.jupiter.api.Test")
      Class.forName("org.junit.platform.runner.JUnitPlatform")
      Class.forName("de.mannodermaus.junit5.AndroidJUnit5")
      true
    } catch (e: Throwable) {
      false
    }
  }

  @Throws(Throwable::class)
  override fun runnerForClass(testClass: Class<*>): Runner? {
    try {
      if (!junit5Available) {
        return null
      }

      if (!testClass.hasJupiterTestMethods()) {
        return null
      }
      return createJUnit5Runner(testClass)

    } catch (e: NoClassDefFoundError) {
      Log.e(LOG_TAG, "JUnitPlatform not found on runtime classpath")
      throw IllegalStateException(
          "junit-platform-runner not found on runtime classpath of instrumentation tests; " +
              "please review your androidTest dependencies or raise an issue.", e)

    } catch (e: Throwable) {
      Log.e(LOG_TAG, "Error constructing runner", e)
      throw e
    }
  }

  /* Extension Functions */

  private fun Class<*>.hasJupiterTestMethods(): Boolean {
    try {
      // Check each method in the Class for the presence
      // of the well-known list of JUnit Jupiter annotations
      val testMethod = declaredMethods.firstOrNull { method ->
        val annotationClassNames = method.declaredAnnotations.map { it.annotationClass.qualifiedName }
        jupiterTestAnnotations.firstOrNull { annotation ->
          annotationClassNames.contains(annotation)
        } != null
      }

      if (testMethod != null) {
        Log.i(LOG_TAG, "Jupiter Test Class detected: ${this.name}")
        return true
      }

      // Recursively check inner classes as well
      declaredClasses.forEach { inner ->
        if (inner.hasJupiterTestMethods()) {
          return true
        }
      }

    } catch (t: Throwable) {
      Log.w(LOG_TAG, "${t.javaClass.name} in 'hasJupiterTestMethods()' for $name", t)
    }

    return false
  }
}
