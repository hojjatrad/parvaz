# وضعیت نسخهٔ 1.26.3 — منتشرشده و بررسی عمومی موفق

نسخهٔ **1.26.3 / code 30** روی کانال پایدار منتشر شد. [انتشار](https://github.com/hojjatrad/parvaz/releases/tag/v1.26.3) · [هش‌ها و شواهد ماشین‌خوان](../releases/v1.26.3-verification.json).

## انتشار و آپدیت داخل برنامه

- انتشار [34389235009](https://github.com/hojjatrad/parvaz/actions/runs/34389235009) موفق است؛ تگ immutable برابر `97835dc9e015ccb59d21701cacf78087196b3bcf` است. تفاوت تگ با کد اجرایی آزموده‌شدهٔ `ef771d5ea5e833ffeb57818f6d917937064158e8` فقط چهار فایل مستنداتی است.
- API عمومیِ بدون توکن نیز latest=v1.26.3، stable/non-draft و چهار asset دقیق را تأیید کرد؛ digest خود GitHub با SHA-256 کامل هر فایل برابر است.
- لینک عمومی releases/latest به v1.26.3 می‌رسد. نام، نسخه و فایل‌های انتشار با مسیر UpdateChecker منطبق‌اند؛ نصب واقعی روی گوشی کاربر هنوز مشاهده نشده است.
- هر چهار فایل، مستقل و کامل به‌صورت جریانی خوانده شدند؛ هش‌های APK و سورس با SHA256SUMS برابرند. فایل حجیم محلی نگه داشته نشد و هر دو APK زیر سقف 128 MiB هستند.
- همان امضای دائمی نسل سوم و بستهٔ com.parvaz.tunnel حفظ شده‌اند. حذف برنامه/داده برای نصب روی نسخه‌های دارای همین امضا لازم نیست؛ تأیید نصب Android لازم است.
- کنترل ترتیب با سورس عمومی، نامزد مصنوعی 1.26.4/code31 را پذیرفت و تکرار 1.26.3/code30 را رد کرد؛ انتشار اضافی انجام نشد. نسخه‌ها و assetهای قبلی بازنویسی نشده‌اند.

| فایل | بایت | SHA-256 |
|---|---:|---|
| Parvaz-1.26.3-arm64.apk | 45376476 | `bb44d266520ca1bdc935a9c031522001ef232694b8235c52002eaaee30c7a087` |
| Parvaz-1.26.3.apk | 88655218 | `0c34a63a8feca0559258d072bd5ec9dd2b1a5abac9ffac47a82533f90fb99a89` |
| Parvaz-1.26.3-source.tar.gz | 477436338 | `998411c5ade3554795b96a0401c625db762af72a9749b4e19f7cac18666d4dcd` |
| SHA256SUMS.txt | 268 | `1b3bad35f2657aa45c0d6d90f45655f75d94228b81810a4b132c97f1a9324176` |

## آزمون‌های کد اجرایی

- [34386833411](https://github.com/hojjatrad/parvaz/actions/runs/34386833411): **199 آزمون، 0 شکست، 0 خطا، 0 skip**، کامپایل و R8 موفق.
- StartupWarmupTest: هفت؛ TrafficSamplingTest: پنج؛ BackupPreservationTest: 22؛ RestoreCrashRecoveryTest: 12؛ RestoreJournalTest: 14 اجرای موفق.
- [34387461482](https://github.com/hojjatrad/parvaz/actions/runs/34387461482): API 29/34 هرکدام 11 آزمون و صفر شکست/خطا/skip؛ هر پنج حالت مرگ واقعی فرایند صریحاً passed هستند. archive fixture همچنان APK عمومی 1.26.1 است، نه نصب نامزد.
- [34387461144](https://github.com/hojjatrad/parvaz/actions/runs/34387461144): lint، واحدها، کلید دائمی، ساخت، امضا/بسته/نسخه/هش/حجم و بسته‌بندی native موفق. سورس آن ساخت پیش از تگ 477,425,508 بایت بود؛ آن فایل با سورس نهایی بالا یکی نیست.
- regression اشتراک [34386833426](https://github.com/hojjatrad/parvaz/actions/runs/34386833426) موفق؛ host شامل 53 assertion refresh/dedup و 25 آزمون ابزار انتشار نیز موفق‌اند.

## تغییرات و مرزها

[شروع فعال ارتباط و آمار اولیه](CONNECTION-STARTUP-fa.md) · [بازیابی بکاپ پس از قطع فرایند](BACKUP-RECOVERY-fa.md).

زمان‌بندی اولین نمونهٔ آمار از 1000 به 250 ms کاهش یافته است؛ این اندازه‌گیری tap-to-internet گوشی یا تضمین صفرشدن RTT/DNS/handshake نیست. warmup از پراکسی خود تونل، با TLS پیش‌فرض، بدون fallback مستقیم، دنبال‌کردن redirect یا دانلود بدنه انجام می‌شود؛ قواعد کانفیگ حفظ می‌شوند و reuse اتصال به پروتکل بستگی دارد.

بازیابی journal، roll-forward کنترل‌شده است؛ مشاهدهٔ هم‌زمان اتمیک برای تمام SharedPreferences یا تضمین قطع برق سخت‌افزاری نیست. backend پروب واقعی مرگ فرایند، fixture است؛ unitها مسیر اصلی BackupManager را می‌سنجند. خرابی journal/کلید به معنی حذف خودکار داده نیست و ممکن است با retry رفع نشود.

گوشی ARM واقعی، نصب درجا، پنل خصوصی، Kill Switch کامل، DNS/IPv6 leak، تغییر شبکه و Doze/باتری هنوز تأیید عملی کامل ندارند. اصلاح تأییدشدهٔ افزایش اتصال‌ها حفظ شده است.
