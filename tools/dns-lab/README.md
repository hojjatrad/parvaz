# آزمایش کش DNS — مسدود برای انتشار

**این پوشه قابلیت آمادهٔ برنامه نیست. آن را به build انتشار وصل نکنید.**

نسخهٔ پایدار همچنان **1.28.2 / code 36** است. این آزمایش نه APK می‌سازد، نه Secret می‌خواند، نه endpoint کنترلی جدیدی در برنامه باز می‌کند و نه هسته‌های نصب‌شده را تغییر می‌دهد. `tools/native/build.py` و pinهای انتشار این patch را مصرف نمی‌کنند.

## چه چیزی واقعاً آزمایش می‌شود؟

- سورس Xray commit `52a412d9e2f5c2a5142b1b4e2ab3771dacb8b120` با SHA-256 آرشیو مشخص.
- حل وابستگی‌ها از ریشهٔ wrapper پین‌شدهٔ AndroidLibXrayLite، نه صرفاً go.mod مستقل Xray. وابستگی‌های لازم برای تست ممکن است به scratch go.mod اضافه شوند؛ graph ثبت می‌شود.
- package واقعی `app/dns`، با `go test -race`؛ این تست JVM/مدل مشابه cache نیست، اما **AAR منتشرشده یا دستگاه Android را اجرا نمی‌کند**.
- درخواست/پاسخ‌های مصنوعی در حافظه‌اند؛ مرحلهٔ UDP علاوه بر آن دو تست upstream را با سوکت محلی loopback اجرا می‌کند. تست انتخاب‌شده هیچ سرور DNS عمومی یا پنل کاربر را هدف نمی‌گیرد؛ دریافت ابزار و dependencyها از اینترنت جداست.

## آزمایش منفی روی سورس بدون patch

پاک‌کردن سادهٔ `ips` سپس تحویل پاسخِ درخواست قبلی، کش را دوباره پر می‌کند و پاسخ را به subscriber جدید می‌رساند. موفقیت این تست یعنی **بازتولید نامعتبر بودن راه‌حل ساده**، نه وجود API flush آسیب‌پذیر در APK فعلی؛ برنامهٔ فعلی چنین APIای ندارد و هسته را بازسازی می‌کند.

## نمونهٔ آزمایشی نسل‌بندی

- درخواست‌ها owner و نسل کنترلر cache را حمل می‌کنند؛ پاسخ بدون scope، owner اشتباه یا نسل قدیمی رد می‌شود.
- کلید subscriber و singleflight شامل نسل است؛ waiterهای قبلی هنگام flush لغو می‌شوند.
- refresh پس‌زمینه، حتی با `WithoutCancel`، نسل قبلی را نگه می‌دارد و نمی‌تواند وارد نسل تازه شود.
- batch و پایان migration قدیمی نمی‌توانند map جدید را دوباره پر یا migration تازه را پاک کنند.
- cleanup نسل قبلی به map جدید دست نمی‌زند. خواننده snapshot جداشدهٔ slot رکورد را می‌گیرد؛ expiry رکورد تمدید نمی‌شود.

این patch **reload سیاست resolver، انتقال cache/TTL، بستن transport قدیمی یا flush کل موتور نیست**. نتیجه‌ای که قبل از مرز flush به outbound تحویل داده شده، با این primitive پس گرفته نمی‌شود.

## کنترل منفیِ مرحلهٔ cache-only: شناسهٔ UDP

`TestParvazKnownLimitReusedUDPTransactionID` از parser و `ClassicNameServer.HandleResponse` واقعی استفاده می‌کند. اگر یک transaction ID به درخواست تازه اختصاص یافته باشد، بستهٔ قدیمیِ دارای همان ID و همان نام پرس‌وجو می‌تواند به scope درخواست جدید نسبت داده شود. این تزریق مصنوعی در حافظه است، نه ادعای مشاهدهٔ آن در شبکهٔ کاربر.

**PASS این canary یعنی مانع بازتولید شده و انتشار همچنان BLOCKED است.** قبل از اتصال این primitive به برنامه، مالکیت transport/درخواست UDP نیز باید مستقل از عدد ۱۶بیتی ID تفکیک شود. تعویض cache به‌تنهایی این مرز را فراهم نمی‌کند. دیگر لایه‌های Xray، fake-DNS/local/system، sing-box و Mihomo و پل Android نیز هنوز پوشش کامل ندارند.

## اجرا و شواهد

با Python 3.12، Go 1.27.0، `patch` و C compiler برای race detector:

```sh
python3 tools/dns-lab/run.py
```

خروجی زیر `.cache/dns-lab/run/` است؛ source/dependencyها در cache هستند و به Git اضافه نمی‌شوند. workflow مستقل `dns-cache-lab.yml` فقط `contents: read` دارد و گزارش‌ها را به artifact کوتاه‌عمر می‌فرستد. نسخهٔ خلاصهٔ شواهد بررسی‌شده در `evidence/` نگه داشته می‌شود.

موفقیت workflow آزمایشگاهی **اجازهٔ انتشار نیست**. `promotion_allowed` باید false باقی بماند تا موانع واقعی با آزمون‌های مستقل رفع شوند؛ صرف حذف شرط BLOCKED یا canary، رفع مانع محسوب نمی‌شود.

## شرایط لازم برای ادامه

1. جداسازی transport/پاسخ UDP در مرز تغییر نسل، همراه با آزمون replay/ID reuse و callback دیررس.
2. پوشش همهٔ cacheها و درخواست‌های درحال‌پرواز در مسیر واقعی هر موتور؛ reload سیاست resolver جدا از خالی‌کردن cache بررسی شود.
3. پل محدود و احرازشده/خصوصی با مالکیت نشست، لغو، timeout و fallback امن به بازسازی؛ کنترلر عمومی صرفاً برای گرفتن HTTP موفق روشن نشود.
4. اتصال به build نگهداری‌شوندهٔ wrapper، source متناظر و تست native Android 10/14؛ گواهی دائمی بدون تغییر بماند.
5. تأیید واقعی HTTPS پس از عملیات؛ پاسخ کنترلر یا RX مدرک اینترنت نیست. تست گوشی/نشت/باتری/Doze همچنان NOT_RUN است.

Patchها و فایل‌های Go مربوط به Xray با SPDX `MPL-2.0` عرضه شده‌اند. افزوده‌های wrapper و تست‌های آن مطابق LICENSE پین‌شدهٔ AndroidLibXrayLite، `LGPL-3.0-only` هستند؛ مجوز سایر فایل‌های پروژه و وابستگی‌ها محفوظ است.

## نتیجهٔ ثبت‌شدهٔ مرحلهٔ اول — تاریخی

- commit آزمایش‌شده: `b46a23c507980f312710d8f7dd22162efd4be27d`.
- [خروجی محلی](evidence/local-result.json): آزمایش منفی بدون patch یک بار؛ 18 آزمون متمایزِ مجموعهٔ patch/کنترل‌ها، هر کدام سه بار با race detector. در اجراهای انتخاب‌شده failure/skip یا گزارش data race ثبت نشد. **آزمون محدودیت UDP جزو این مجموعه است و PASS آن یعنی مانع بازتولید شده، نه رفع‌شده.**
- [شواهد CI](evidence/ci-summary.json): آزمایشگاه مستقل، ساخت/آزمون واحد Android و رگرسیون subscription موفق شدند؛ 387 آزمون واحد برنامه همچنان موفق‌اند. این‌ها آزمون نصب APK جدید یا گوشی نیستند.
- [مرز انتشار](evidence/production-boundary.json): main، تگ v1.28.2 و کد/build/pinهای تولیدی بررسی و بدون تغییر باقی مانده‌اند. hash/حجم/نام assetهای انتشار پایدار نیز با شواهد قبلی تطبیق داده شدند.
- **مجوز انتشار این قابلیت: BLOCKED.** مرحلهٔ بعد، مالکیت transport UDP و سپس پوشش همهٔ لایه‌ها/پل Android است. هیچ نسخهٔ جدید APK برای این آزمایش منتشر نشده است.


## مرحلهٔ دوم — جداسازی مالکیت transport UDP

Runner اکنون سه مرحلهٔ مستقل دارد؛ **دو کنترل منفی قبلی حذف یا به موفقیت امنیتی تبدیل نشده‌اند**:

| مرحله | اجرای سطح اول | تفسیر |
|---|---:|---|
| baseline بدون patch | 1 | بازتولید شکست پاک‌کردن ساده |
| cache-only | 18 آزمون × 3 = 54 | نسل‌بندی cache موفق، محدودیت ID همچنان بازتولید می‌شود |
| udp-isolated | 39 آزمون × 3 = 117 | مرز callback قدیمی بسته می‌شود؛ محدودیت تزریق بایت در مسیر جدید جداگانه بازتولید می‌شود |

`xray-udp-transport.patch` **بعد از** patch نسل‌بندی، فقط مسیر DNS کلاسیک Xray را به transport خصوصی هر نسل متصل می‌کند؛ dispatcher عمومی UDP برای سایر ترافیک دست‌کاری نشده است.

- reader هر transport به همان map درخواست‌ها متصل است، نه map آخرین transport سرور. callback قدیمی حتی با ID و پرس‌وجوی یکسان، درخواست جدید را مصرف نمی‌کند.
- هر transport حداکثر 65,536 شناسهٔ مصرف‌نشده دارد؛ پس از اتمام، قبل از استفادهٔ دوباره کنار گذاشته می‌شود. درخواست‌های باقی‌ماندهٔ آن ممکن است خطا بگیرند؛ عدم قطع/بدون‌وقفه بودن تضمین نشده است.
- حداکثر **دو transport دارای مالکیت منابع** برای هر nameserver، 256 درخواست منتظر در هر transport و یک write هم‌زمان پذیرفته می‌شود. اشباع خطای صریح می‌دهد، نه صف یا goroutine نامحدودِ ساخته‌شده توسط این driver. این سقف، شمار همهٔ callerها یا workerهای داخلی routing engine نیست.
- لغو waiter، خروج native initializer/read/write محسوب نمی‌شود. ظرفیت تا برگشت عملیات و پایان cleanup موفق نگه داشته می‌شود. cleanup دارای خطای صریح نیز ظرفیت را آزادشده اعلام نمی‌کند. initializer/reader/writer/cleanup عمداً ناسازگار با لغو در fixtureها آزمایش شده‌اند.
- هیچ فراخوانی routing، خواندن، نوشتن یا بستن native زیر قفل سراسری cache/سرور اجرا نمی‌شود. retirement و انتشار پاسخ با قفل محلی و guard نسل سریال می‌شوند.
- پایان یک caller مالک عمر transport مشترک نیست. timeout بیکاری یک دقیقه‌ای فعالیت تازه را دوباره بررسی می‌کند؛ deadline یک write درحال‌اجرا می‌تواند برای جمع‌کردن آن transport را کنار بگذارد و درخواست‌های هم‌مسیر خطا بگیرند.
- QR/opcode، دقیقاً یک سؤال، نام/نوع/class، expiry و EDNS retry بررسی می‌شوند. retry همان owner و ID تازه دارد؛ پاسخ truncated قدیمی نمی‌تواند retry تازه بسازد و truncation دوباره حلقهٔ retry نمی‌سازد.

### مرزهای ادعا و fixture

`xray-udp-loopback-fixture.patch` **فقط تست upstream** را از `ans.Id = r.Id` به `ans.SetReply(r)` اصلاح می‌کند: پاسخ آزمایشی باید QR و سؤال معتبر داشته باشد. برای عبور از تست، اعتبارسنجی پاسخ در driver ضعیف نشده است. دو تست محلی `TestUDPServer` و `TestUDPServerSubnet` از dispatcher واقعی Xray و DNS loopback استفاده می‌کنند؛ این Android/TUN، DNS عمومی یا شبکهٔ کاربر نیست.

`TestParvazKnownLimitReplayInjectedIntoNewTransport` عمداً بایت قدیمی با ID/سؤال یکسان را **به لینک جدید** تزریق می‌کند و پذیرفته‌شدن آن را ثبت می‌کند. این با callback لینک قدیمی فرق دارد: این primitive اصالت/تازگی رمزنگاری‌شدهٔ UDP را اثبات نمی‌کند. تخصیص مجدد tuple سوکت در OS، رفتار proxy راه دور، جعل/بازپخش روی شبکه و source filtering هر outbound با fixture درون‌حافظه‌ای اثبات نشده‌اند. TLS ضعیف نشده، direct fallback یا کنترلر عمومی اضافه نشده است.

**هنوز BLOCKED:** API/توقف و lifecycle واقعی wrapper Android، reload سیاست resolver، cacheهای دیگر Xray و sing-box/Mihomo/system/fake-DNS و انتقال TTL کامل نشده‌اند. این مرحله تغییر APK، تضمین سرعت یا آزمون گوشی نیست.

شواهد محلی مرحلهٔ دوم: [سه مرحلهٔ کامل](evidence/udp-local-result.json) با graph وابستگی یکسان و SHA ورودی‌ها؛ [اجرای فشار تصادفی](evidence/udp-stress-result.json)، 20 آزمون UDP × 30 = 600 اجرای سطح اول با race detector و بدون failure/skip. این عدد شامل canary محدودیتِ مسیر جدید هم هست. نتیجهٔ CI باید جداگانه برای commit دقیق ثبت شود؛ اجرای محلی جای آن نیست.

### نتیجهٔ تأییدشدهٔ مرحلهٔ دوم

- commit کد و ورودی‌های آزمایش‌شده: `406dccb062b1165f4784e8d56505ec3278ad8d77`.
- [CI آزمایشگاه](https://github.com/hojjatrad/parvaz/actions/runs/34540082889)، [ساخت/387 آزمون واحد Android](https://github.com/hojjatrad/parvaz/actions/runs/34540082902) و [رگرسیون subscription](https://github.com/hojjatrad/parvaz/actions/runs/34540082905) برای همین commit موفق شدند؛ annotation عمومی آزمایشگاه `baseline=1 patched=54 udp-isolated=117` و **PROMOTION_BLOCKED** را تأیید کرد.
- [خلاصهٔ CI](evidence/udp-ci-summary.json) و [مرز production](evidence/udp-production-boundary.json) ثبت شدند. main/تگ و نام/حجم/digest تمام assetهای آخرین پایدار با شواهد تأیید کامل قبلی برابر ماندند؛ این مرحله APKها را دوباره دانلود یا روی گوشی نصب نکرد.
- [لاگ‌های فشردهٔ اجرای محلی و فشار](evidence/udp-local-logs.tar.gz)، 96,365 بایت، SHA-256: `71506b3f405fdaf4f60a36e64bb55b69b336b7d6441a5452ad40dba556cb4156`. hash فایل‌های بازشده با گزارش‌های JSON قابل تطبیق است؛ وابستگی‌ها/ابزارهای حجیم وارد Git نشده‌اند.
- callback مسیر قدیمی در این مرز آزمایش‌شده جدا شد؛ تزریق پاسخ به خود مسیر جدید، پوشش سایر cacheها، lifecycle/bridge واقعی Android و اعتبارسنجی گوشی هنوز از ادعای تکمیل خارج‌اند. **هیچ APK، تگ انتشار یا امضایی تغییر نکرد.**


## مرحلهٔ سوم — عمر کل lookup و پل خصوصیِ آزمایشی wrapper

### دو شکاف بازتولیدشده، پیش از اصلاح

مرحلهٔ مستقل `aggregate-control` بعد از patch UDP ولی **بدون** patch جدید اجرا می‌شود:

1. درخواست تجمیعی از resolver اول شروع شده؛ cacheهای resolver دوم و سپس اول flush می‌شوند. `DNS.serialQuery` قدیمی می‌تواند fallback را با نسل تازه شروع کند و نتیجه را به درخواست قدیمی بدهد. این تست از `DNS.LookupIP` واقعی، دو ClassicNameServer و routing-pipeهای آزمایشی استفاده می‌کند.
2. `DNS.Close` پین‌شده noop است و lookup تازه پس از آن هنوز از cache پاسخ می‌گیرد.

این‌ها محدودیت‌های نمونهٔ قبل از patch جدید هستند، **نه ادعای وجود API flush ناقص در APK پایدار**. PASS این دو کنترل منفی یعنی بازتولید شکاف‌ها؛ حذف نشده‌اند.

### مرز جدید Xray

`xray-dns-lifetime.patch` پس از patchهای قبلی اعمال می‌شود:

- نسل/بستن در سطح کل `DNS.LookupIP` محافظت می‌شود، نه فقط یک resolver. snapshot تغییرناپذیرِ scope تمام cache-controllerها در context ذخیره می‌شود؛ fallback و context جداشده با `WithoutCancel` نمی‌توانند scope تازه قرض بگیرند.
- تعویض نسل همهٔ cacheهای پذیرفته‌شده نسبت به ورود lookup جدید سریال است. lookup قدیمی و نتیجهٔ دیررس، نسل جدید را معتبر نمی‌کنند؛ نتیجه‌ای که قبلاً در نقطهٔ تحویل پذیرفته شده قابل پس‌گرفتن نیست.
- `Close` ورود و انتشار منطقی را می‌بندد؛ controller بسته‌شده با flush دوباره زنده نمی‌شود. cleanup/migration دیررس، حتی روی سقف uint64، cache بسته را بازسازی نمی‌کند. شروع timerهای cleanup نیز با بستن هماهنگ است.
- parallel lookup هنگام بستن می‌تواند انتظار را رها کند، ولی خروج یک child ناسازگار با لغو **ادعا نمی‌شود**. کانال نتیجه ظرفیت خروج child را نگه می‌دارد و fixture آن را پس از آزادسازی واقعی جمع می‌کند.
- preflight قبل از هر mutation، تمام nameserverها را بررسی می‌کند. فعلاً فقط مجموعهٔ محدود به Classic UDP، بدون انتخاب مسیر سیستم، و حداکثر 64 client برای فرمان منطقی پذیرفته می‌شود. Local/Fake/DoH/TCP/QUIC/ناشناخته یا ترکیب آنها از این فرمان رد می‌شوند؛ یک cache از ترکیب ناقص پاک نمی‌شود. این محدودیت مسیر lookup عادی/پیکربندی را بازنویسی نمی‌کند.
- static hosts و سیاست resolver تغییر نمی‌کنند؛ حفظ پاسخ static پیش/پس از invalidation و رد همان feature پس از Close با wrapper/core واقعی آزمایش شده است.

### پل خصوصی، بدون endpoint یا ادعای موفقیت کامل

`wrapper-dns-lifetime.patch` و `wrapper-dns-control.go` فقط در ریشهٔ موقت wrapper کپی/کامپایل می‌شوند. `ParvazLabDNSState` و `ParvazLabInvalidateDNS` روش‌های **درون‌پردازه‌ای آزمایشی** هستند؛ HTTP controller، Secret جدید یا مسیر direct fallback ندارند.

- stamp تصادفی هر Start موفق، controller/نمونه را تفکیک می‌کند. Stop قبل از بستن native آن را باطل می‌کند؛ Start بدون تغییرِ هسته stamp را نگه می‌دارد. فرمان نشست قبلی به نمونهٔ بعدی یا controller دیگر نمی‌رسد.
- revision به‌شکل رشتهٔ ده‌دهی canonical منتقل و با compare-and-swap بررسی می‌شود؛ فرمان تکراری حداکثر یک بار اعمال می‌شود. خطای ورودی/نشست قدیمی پیش از mutation رد می‌شود.
- `TryLock` فرمان را پشت Start/Stop مشغول صف نمی‌کند؛ عملیات کنترل هیچ خواندن/نوشتن/بستن native زیر قفل lifecycle انجام نمی‌دهد. retirement واقعی در reaper متعلق به transport انجام می‌شود.
- مالکیت UDP بازنشسته در **همان CoreController** تا خروج واقعی نگه داشته می‌شود. اگر initializer قدیمی هنوز برگشت نکرده، StartLoop بعدی خطای صریح می‌دهد؛ پس از خروج و cleanup می‌تواند دوباره شروع شود. این gate، process-global یا محافظ ساخت controller جدید توسط Android نیست.
- پاسخ همیشه `full_chain_flushed=false` و `rebuild_required=true` دارد. حتی `LOGICAL_INVALIDATED_REBUILD_REQUIRED` فقط اعمال مرز منطقی را اعلام می‌کند، نه تخلیهٔ native، اینترنت تأییدشده یا reload کامل. `owned_udp_leases=0` نیز مدرک تخلیهٔ سایر transportها نیست.

### پوشش و محدودیت اجرا

| مرحله | اجرای سطح اول |
|---|---:|
| baseline | 1 |
| cache-only | 18 × 3 = 54 |
| UDP-isolated | 39 × 3 = 117 |
| aggregate-control منفی | 2 × 1 = 2 |
| lifetime Xray | 53 × 3 = 159 |
| private-wrapper واقعی | 11 × 3 = 33 |

[شواهد محلی](evidence/lifetime-local-result.json) و [فشار تصادفی](evidence/lifetime-stress-result.json): 14 آزمون lifetime × 30 و 11 آزمون wrapper × 30، در مجموع **750 اجرای فشار** با race detector، بدون failure/skip. graph وابستگی تمام مراحل یکسان و ورودی‌ها SHA-256 شده‌اند. [لاگ‌های کامل فشرده](evidence/lifetime-local-logs.tar.gz): 168,389 بایت؛ SHA-256 `1db154ba89731096dd7292dab3c024a5ab6fe2286bd8e2958145291a00f07376`.

wrapper با `StartLoop/StopLoop` و `core.Instance` واقعی، روی **Linux، بدون TUN/inbound و با outbound آزمایشی blackhole** اجرا شده است؛ fixture initializer ناسازگار از routing تزریقی استفاده می‌کند. این **gomobile binding، فایل‌های مخصوص Android، AAR منتشرشده یا اجرای Android 10/14 نیست**. دو تست loopback قبلی همچنان جداگانه وجود دارند. CI Android برنامهٔ پایدار، حتی اگر موفق شود، این patch آزمایشگاهی را مصرف نمی‌کند.

### هنوز مانع انتشار

- اتصال Android و مالکیت سراسری میان controllerهای تازه/نمونهٔ probe و VPN هنوز ساخته/اثبات نشده است؛ gate همان controller جای آن را نمی‌گیرد.
- چرخهٔ عمر native همهٔ انواع DNS، سایر cacheهای Xray، sing-box/Mihomo، system/fake-DNS و reload/انتقال TTL کامل نیست.
- محدودیت بازپخش بایت قدیمی روی خود لینک UDP جدید از مرحلهٔ قبل پابرجاست؛ این primitive اصالت شبکه را اثبات نمی‌کند.
- تست نصب/گوشی، نشت DNS/IPv6، Doze، جابه‌جایی شبکه و باتری همچنان NOT_RUN است. سرعت یا اتصال بدون‌وقفه تضمین نشده است.

**APK و گواهی پایدار 1.28.2 تغییر نکرده‌اند؛ این مرحله نیز PROMOTION_BLOCKED است.** شواهد CI باید برای commit دقیق جداگانه ثبت شود.
