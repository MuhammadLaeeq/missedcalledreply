# Missed Call Reply (Android)

Missed call hone par caller ko WhatsApp (aur chaho to SMS) par automatic message bhejta hai.
Website, Google Business Profile, kahin se bhi call aaye, sab par kaam karta hai.

## Kaise kaam karta hai

1. `CallReceiver` har call ki state dekhta hai. Ringing ke baad call pick hue bina band ho gayi = missed call.
2. Number queue mein jata hai (saved contacts aur cooldown wale skip).
3. `AutoSendService` (Accessibility) WhatsApp chat message ke saath kholti hai, Send dabati hai, aur home par wapas aa jati hai.
4. Phone lock ho to message unlock hote hi chala jata hai.

## APK banane ke 2 tareeqe

### A) Android Studio (computer par)
1. Android Studio install karo, ye folder `File > Open` se kholo.
2. Gradle sync hone do (pehli baar thoda time lagta hai).
3. `Build > Build Bundle(s) / APK(s) > Build APK(s)`.
4. APK yahan milegi: `app/build/outputs/apk/debug/app-debug.apk`

### B) GitHub se free (Android Studio ki zaroorat nahi)
1. GitHub par naya repo banao aur ye poora folder upload karo (`.github` folder samait).
2. Repo mein **Actions** tab kholo, build khud chal jayega (3 se 5 minute).
3. Build complete hone par neeche **Artifacts** mein `MissedCallReply-apk` download karo.

## Phone par setup

1. APK install karo (Settings mein "Install unknown apps" allow karna padega).
2. App kholo aur teeno steps karo:
   - Step 1: Permissions (Phone, Call log, Contacts, SMS)
   - Step 2: Accessibility > Missed Call Reply > ON
     (Android 13+ par agar "Restricted setting" aaye: Settings > Apps > Missed Call Reply > upar 3 dots > "Allow restricted settings", phir dobara on karo)
   - Step 3: Battery optimization band
3. Message likho, WhatsApp Business ya Normal choose karo, Save.
4. "Test message bhejo" se apne doosre number par test karo.
5. Asli test: doosre phone se call karo, pick mat karo.

Samsung / Xiaomi / Oppo phones par extra: app ko "Autostart" allow karo aur recent apps mein lock kar do,
warna ye phones background apps band kar dete hain.

## Limitations

- Sirf Android. iPhone par ye mumkin nahi.
- Phone on aur internet chalu hona chahiye.
- Jis caller ka WhatsApp nahi, usay WhatsApp message nahi jayega (is liye SMS option rakha hai).
- WhatsApp apna design badle to Send button ka ID badal sakta hai. Phir `AutoSendService.kt` mein `findSendButton` update karna hoga.
- Ye unofficial automation hai. Normal business volume theek hai, bulk messaging ke liye mat use karna.
