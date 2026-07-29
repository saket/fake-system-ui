package me.saket.fakesystemui.internal

import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.annotation.RequiresApi
import androidx.compose.runtime.RememberObserver
import kotlinx.coroutines.flow.MutableStateFlow
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.WeakHashMap

/**
 * Observes system bar visibility requests made through [WindowInsetsController].
 *
 * Layoutlib creates a real platform insets controller, but does not give it controllable
 * status/navigation bar sources. As a result, hide/show requests are recorded internally and
 * then dropped before an animation can start. This observer wraps the controller's layoutlib host
 * and observes those recorded requests without replacing the controller used by application code.
 */
@RequiresApi(30)
internal class WindowInsetsControllerObserver(
  private val controller: WindowInsetsController,
) : RememberObserver {
  /**
   * The controller's current requested-visible inset types, updated whenever
   * [WindowInsetsController.hide] or [WindowInsetsController.show] changes them.
   *
   * The [Int] value is the combined bitmask of all inset types currently requested visible, not
   * the `typeMask` argument passed to the most recent `hide()` or `show()` call. Calling
   * `show(typeMask)` adds those bits to the emitted mask, and calling `hide(typeMask)` removes
   * those bits. For example, callers can test whether system bars are requested visible with:
   *
   * ```
   * requestedVisibleTypes and WindowInsets.Type.systemBars() == WindowInsets.Type.systemBars()
   * ```
   */
  val requestedVisibleTypes = MutableStateFlow(WindowInsets.Type.systemBars())

  private val subscription = registerOnRequestedVisibleTypesChanged { visibleTypes ->
    requestedVisibleTypes.value = visibleTypes
  }

  override fun onRemembered() = Unit

  override fun onForgotten() = close()

  override fun onAbandoned() = close()

  fun close() {
    subscription.close()
  }

  private fun registerOnRequestedVisibleTypesChanged(listener: (Int) -> Unit): AutoCloseable {
    val observation = synchronized(observations) {
      observations.getOrPut(controller) {
        InsetsControllerObservation.tryCreate(controller) ?: return AutoCloseable {}
      }
    }

    synchronized(observation) {
      observation.listeners += listener
      listener(observation.requestedVisibleTypes)
    }
    return AutoCloseable {
      val shouldRemove = synchronized(observation) {
        observation.listeners -= listener
        if (observation.listeners.isEmpty()) {
          observation.close()
          true
        } else {
          false
        }
      }
      if (shouldRemove) {
        synchronized(observations) {
          if (observations[controller] === observation) {
            observations -= controller
          }
        }
      }
    }
  }

  private companion object {
    // The JDK doesn't include a concurrent weak-key map. Keep this map guarded by itself so
    // lookup, proxy installation, and restoration stay atomic for each controller.
    private val observations = WeakHashMap<WindowInsetsController, InsetsControllerObservation>()
  }

  /**
   * Owns one installed host proxy for a layoutlib [WindowInsetsController].
   *
   * Multiple callers can observe the same controller, so this object keeps the shared listener
   * list, the latest requested visible inset types, and the original host needed for restoration.
   */
  private class InsetsControllerObservation(
    controller: WindowInsetsController,
    private val requestedVisibleTypesGetter: Method,
    private val restoreOriginalHost: () -> Unit,
    initialRequestedVisibleTypes: Int,
  ) {
    // The controller is weakly referenced because [observations] should not keep
    // layoutlib views or controllers alive after a snapshot is finished.
    private val controller = WeakReference(controller)

    val listeners = mutableSetOf<(Int) -> Unit>()
    var requestedVisibleTypes = initialRequestedVisibleTypes
      private set

    fun readRequestedVisibleTypes() {
      val controller = controller.get() ?: return
      val requestedVisibleTypes = requestedVisibleTypesGetter.invoke(controller) as Int
      if (requestedVisibleTypes != this.requestedVisibleTypes) {
        this.requestedVisibleTypes = requestedVisibleTypes
        for (listener in listeners.toList()) {
          listener(requestedVisibleTypes)
        }
      }
    }

    fun close() {
      restoreOriginalHost()
    }

    companion object {
      /**
       * Reflection is scoped to layoutlib's controller internals, but avoids depending on
       * private names where possible. The requested visible types are read through a public
       * method, the host field is found by looking for the interface-typed field that currently
       * holds the controller's own host instance, and the proxy forwards every host call before
       * observing state. If any of these assumptions stop being true, the bridge declines to
       * install or keeps forwarding calls instead of breaking snapshot rendering.
       */
      fun tryCreate(controller: WindowInsetsController): InsetsControllerObservation? {
        return try {
          val requestedVisibleTypesGetter = controller.javaClass.getMethod("getRequestedVisibleTypes")
            .takeIf { it.returnType == Int::class.javaPrimitiveType }
            ?: return null

          val originalHost = controller.javaClass.getMethod("getHost").invoke(controller)
            ?: return null

          var hostField: Field? = null
          var controllerClass: Class<*>? = controller.javaClass
          while (controllerClass != null && hostField == null) {
            hostField = controllerClass.declaredFields.firstOrNull {
              it.isAccessible = true
              it.type.isInterface && it.get(controller) === originalHost
            }
            controllerClass = controllerClass.superclass
          }
          if (hostField == null) {
            return null
          }

          lateinit var observation: InsetsControllerObservation
          val proxyHost = Proxy.newProxyInstance(
            /* loader = */ hostField.type.classLoader,
            /* interfaces = */ arrayOf(hostField.type),
          ) { _, method, args ->
            try {
              method.invoke(originalHost, *(args ?: emptyArray()))
            } catch (e: InvocationTargetException) {
              throw e.targetException
            } finally {
              try {
                observation.readRequestedVisibleTypes()
              } catch (_: ReflectiveOperationException) {
                // Keep forwarding controller calls if a future layoutlib changes its internals
                // after this bridge was installed.
              }
            }
          }
          observation = InsetsControllerObservation(
            controller = controller,
            requestedVisibleTypesGetter = requestedVisibleTypesGetter,
            initialRequestedVisibleTypes = requestedVisibleTypesGetter.invoke(controller) as Int,
            restoreOriginalHost = {
              if (hostField.get(controller) === proxyHost) {
                hostField.set(controller, originalHost)
              }
            },
          )
          hostField.set(controller, proxyHost)
          if (hostField.get(controller) !== proxyHost) {
            return null
          }
          observation
        } catch (_: ReflectiveOperationException) {
          null
        } catch (_: SecurityException) {
          null
        }
      }
    }
  }
}
