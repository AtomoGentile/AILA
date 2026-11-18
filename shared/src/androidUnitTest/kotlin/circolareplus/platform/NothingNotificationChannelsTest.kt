package circolareplus.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
import circolareplus.design.NothingNotificationGroup
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],manifest=Config.NONE)
class NothingNotificationChannelsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun useNothingDevice() {
        ShadowBuild.setManufacturer("Nothing")
    }

    @Test fun enabledPreferenceOnOtherDevicesKeepsTheRegularChannel() {
        ShadowBuild.setManufacturer("Google")
        val manager = context.getSystemService(NotificationManager::class.java)
        assertEquals("aila_notifications", NothingNotificationChannels.route(context, "circulars", true))
        assertNull(manager.getNotificationChannel(NothingNotificationChannels.channelId(NothingNotificationGroup.CIRCULARS)))
    }

    @Test fun splitChannelsPreserveBlockedLegacyAndUserEdits() {
        val manager=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel("aila_notifications","AILA",NotificationManager.IMPORTANCE_NONE))
        NothingNotificationChannels.ensure(context)
        assertEquals("aila_notifications",NothingNotificationChannels.route(context,"forum",true))
        assertEquals(NotificationManager.IMPORTANCE_NONE,manager.getNotificationChannel(NothingNotificationChannels.channelId(NothingNotificationGroup.FORUM)).importance)
    }
    @Test fun routingAndSettingsTargetTheSameCategoryWithoutChangingExistingSound() {
        val manager=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val group=NothingNotificationGroup.POLLS
        val id=NothingNotificationChannels.channelId(group)
        val custom=NotificationChannel(id,"Scelta utente",NotificationManager.IMPORTANCE_LOW)
        custom.setSound(null,null)
        manager.createNotificationChannel(custom)
        NothingNotificationChannels.ensure(context)
        assertEquals(id,NothingNotificationChannels.route(context,"ranking_polls",true))
        assertEquals(NotificationManager.IMPORTANCE_LOW,manager.getNotificationChannel(id).importance)
        assertNull(manager.getNotificationChannel(id).sound)
        assertTrue(NothingNotificationChannels.open(context,group))
        val intent=shadowOf(context).nextStartedActivity
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS,intent.action)
        assertEquals(id,intent.getStringExtra(Settings.EXTRA_CHANNEL_ID))
        assertEquals(context.packageName,intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        assertEquals("aila_notifications",NothingNotificationChannels.route(context,"polls",false))
    }
}
