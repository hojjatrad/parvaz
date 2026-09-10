# مرز مدیریت کش DNS در نسخهٔ ۱٫۲۸٫۰

## بررسی روی pinهای فعلی

- [Mihomo cache router](https://raw.githubusercontent.com/MetaCubeX/mihomo/ac017cdd246ce8bd547653d927e7bf77d7ee73d5/hub/route/cache.go): مسیرهای `/fakeip/flush` و `/dns/flush` دیده می‌شوند؛ دومی `resolver.ClearCache()` را فراخوانی می‌کند.
- [sing-box cache router](https://raw.githubusercontent.com/SagerNet/sing-box/0b8995879f29a9b98ee027bc17b75e101445b238/experimental/clashapi/cache.go): مسیر `/dns/flush` به `DNSRouter.ClearCache()` می‌رسد؛ fake-IP جداگانه reset می‌شود.

این دو router، قرارداد عمومی export/import رکورد DNS همراه TTL باقی‌مانده و سیاست resolver ارائه نمی‌کنند. این مشاهده ادعای نبود هر API ممکن در همهٔ نسخه‌ها نیست. `EngineConfig` پرواز نیز کنترلرهای Mihomo و تنظیمات experimental مربوط به sing-box را برای نمونهٔ مدیریت‌شده حذف می‌کند؛ در این نسخه endpoint کنترلی جدیدی باز نشده است.

## تصمیم این مرحله

انتقال فایل یا دادهٔ cache بین موتورهای متفاوت انجام نمی‌شود. در تغییر شناخته‌شدهٔ DNS/نام resolver، نمونهٔ هسته بازسازی می‌شود و نمونهٔ قدیمی نمی‌تواند برای سیاست جدید مدرک سلامت صادر کند. با resolver ثابت، بررسی موفق می‌تواند نمونهٔ زنده را نگه دارد. تغییر گذرای وضعیت اعتبارسنجی Private DNS از تغییر هویت resolver جداست و به‌تنهایی cache را بازسازی نمی‌کند.

برای پاک‌سازی درجا بدون قطع سوکت‌ها، مرحلهٔ بعد به یک مسیر کنترل محدود و احرازشده، پوشش cache در لایهٔ Xray/رله، و آزمون مالکیت/لغو/timeout نیاز دارد؛ این قابلیت در این انتشار پیاده نشده است. افزودن صرفاً یک درخواست HTTP به دو موتور، پاک‌شدن همهٔ cacheهای زنجیره را ثابت نمی‌کند.

سن مدرک HTTPS از elapsedRealtime اندروید استفاده می‌کند تا خواب دستگاه زمان اعتبار را متوقف نکند. آزمون ساعت مجازی، جای اندازه‌گیری واقعی Doze یا باتری را نمی‌گیرد. زمان مستقل DNS هنوز NOT_INSTRUMENTED است.
