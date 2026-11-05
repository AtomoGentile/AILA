package circolareplus.platform

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM values, with a non-exportable key in Android Keystore. Excluded from backup. */
actual fun createSecureSettings(): Settings? {
 val context=AndroidAppContext.require()
 val prefs=SharedPreferencesSettings(context.getSharedPreferences("aila_secrets",0))
 return object:Settings by prefs {
  private fun secretKey():SecretKey {
   val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
   (store.getKey("aila.account.secrets.v1",null) as? SecretKey)?.let{return it}
   return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{
    init(KeyGenParameterSpec.Builder("aila.account.secrets.v1",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
     .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
     .setRandomizedEncryptionRequired(true).build())
   }.generateKey()
  }
  override fun putString(key:String,value:String){
   val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,secretKey())
   cipher.updateAAD(key.encodeToByteArray())
   val data=cipher.iv+cipher.doFinal(value.encodeToByteArray())
   prefs.putString(key,Base64.encodeToString(data,Base64.NO_WRAP))
  }
  override fun getStringOrNull(key:String):String? {
   val encoded=prefs.getStringOrNull(key)?:return null
   val bytes=Base64.decode(encoded,Base64.NO_WRAP)
   require(bytes.size>=28){"Archivio credenziali non disponibile"}
   val cipher=Cipher.getInstance("AES/GCM/NoPadding")
   cipher.init(Cipher.DECRYPT_MODE,secretKey(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
   cipher.updateAAD(key.encodeToByteArray())
   return cipher.doFinal(bytes.copyOfRange(12,bytes.size)).decodeToString()
  }
  override fun getString(key:String,defaultValue:String):String=getStringOrNull(key)?:defaultValue
 }
}
