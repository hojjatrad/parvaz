# مرز مدیریت کش DNS در نسخهٔ ۱٫۲۸٫۰

## بررسی روی pinهای فعلی

- [Mihomo cache router](https://raw.githubusercontent.com/MetaCubeX/mihomo/ac017cdd246ce8bd547653d927e7bf77d7ee73d5/hub/route/cache.go): مسیرهای `/fakeip/flush` و `/dns/flush` دیده می‌شوند؛ دومی `resolver.ClearCache()` را فراخوانی می‌کند.
- [sing-box cache router](https://raw.githubusercontent.com/SagerNet/sing-box/0b8995879f29a9b98ee027bc17b75e101445b238/experimental/clashapi/cache.go): مسیر `/dns/flush` به `DNSRouter.ClearCache()` می‌رسد؛ fake-IP جداگانه reset می‌شود.

این دو router، قرارداد عمومی export/import رکورد DNS همراه TTL باقی‌مانده و سیاست resolver ارائه نمی‌کنند. این مشاهده ادعای نبود هر API ممکن در همهٔ نسخه‌ها نیست. `EngineConfig` پرواز نیز کنترلرهای Mihomo و تنظیمات experimental مربوط به sing-box را برای نمونهٔ مدیریت‌شده حذف می‌کند؛ در این نسخه endpoint کنترلی جدیدی باز نشده است.

## تصمیم این مرحله

انتقال فایل یا دادهٔ cache بین موتورهای متفاوت انجام نمی‌شود. در تغییر شناخته‌شدهٔ DNS/نام resolver، نمونهٔ هسته بازسازی می‌شود و نمونهٔ قدیمی نمی‌تواند برای سیاست جدید مدرک سلامت صادر کند. با resolver ثابت، بررسی موفق می‌تواند نمونهٔ زنده را نگه دارد. تغییر گذرای وضعیت اعتبارسنجی Private DNS از تغییر هویت resolver جداست و به‌تنهایی cache را بازسازی نمی‌کند.

برای پاک‌سازی درجا بدون قطع سوکت‌ها، مرحلهٔ بعد به یک مسیر کنترل محدود و احرازشده، پوشش cache در لایهٔ Xray/رله، و آزمون مالکیت/لغو/timeout نیاز دارد؛ این قابلیت در این انتشار پیاده نشده است. افزودن صرفاً یک درخواست HTTP به دو موتور، پاک‌شدن همهٔ cacheهای زنجیره را ثابت نمی‌کند.

سن مدرک HTTPS از elapsedRealtime اندروید استفاده می‌کند تا خواب دستگاه زمان اعتبار را متوقف نکند. آزمون ساعت مجازی، جای اندازه‌گیری واقعی Doze یا باتری را نمی‌گیرد. زمان مستقل DNS هنوز NOT_INSTRUMENTED است.

## بررسی تکمیلی برای ۱٫۲۸٫۲: لایهٔ Xray

نسخهٔ wrapper در [go.mod پین‌شده](https://raw.githubusercontent.com/2dust/AndroidLibXrayLite/d0c6c4ae1b09c912070c8288bd0dbcc2e492ac29/go.mod) به Xray commit `52a412d9e2f5` وابسته است.

- [features/dns/client.go](https://raw.githubusercontent.com/XTLS/Xray-core/52a412d9e2f5/features/dns/client.go): رابط Client بررسی‌شده LookupIP و lifecycle دارد؛ قرارداد flush کامل/export/import در همین رابط نیست.
- [app/dns/cache_controller.go](https://raw.githubusercontent.com/XTLS/Xray-core/52a412d9e2f5/app/dns/cache_controller.go): CacheCleanup رکوردهای منقضی را جمع می‌کند؛ migrate/flush داخلی برای جابه‌جایی map همان نمونه است، نه انتقال بین موتورهای متفاوت. تغییر خالی‌کردن map به‌تنهایی، پاسخ‌های درحال‌پرواز و dirtyips را پوشش نمی‌دهد.
- [app/dns/nameserver_cached.go](https://raw.githubusercontent.com/XTLS/Xray-core/52a412d9e2f5/app/dns/nameserver_cached.go): singleflight، pubsub و refresh پس‌زمینه وجود دارند. flush امن به مرز نسل برای پاسخ‌های دیررس هم نیاز دارد؛ فقط «HTTP 204 از کنترلر موتور دیگر» کافی نیست.

این بررسی ادعای نبود هر API ممکن در کل پروژه نیست. در ۱٫۲۸٫۲ تغییر binary/کنترلر برای flush اضافه نشده؛ اصلاح عملی به حفظ پیوستگی snapshot DNS در قطع/بازگشت و callback دیرهنگام محدود است. توسعهٔ پل محدود برای flush باید جداگانه با آزمون native و مالکیت نشست/نسل انجام شود.

## شرط build برای API سینگ‌باکس

[include/clashapi.go در pin فعلی](https://raw.githubusercontent.com/SagerNet/sing-box/0b8995879f29a9b98ee027bc17b75e101445b238/include/clashapi.go) با `//go:build with_clash_api`، بستهٔ clashapi را ثبت می‌کند. این tag در فهرست build فعلی `tools/native/engines-lock.json` انتخاب نشده است. بنابراین صرف وجود فایل router در upstream، مجوز فرض‌کردن دسترس‌پذیری آن در binary فعلی نیست؛ فعال‌کردن احتمالی به تغییر build، آزمون و کنترل محدود/احرازشده نیاز دارد. این تغییر در ۱٫۲۸٫۲ انجام نشده است.
