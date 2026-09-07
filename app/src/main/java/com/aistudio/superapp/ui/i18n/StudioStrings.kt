package com.aistudio.superapp.ui.i18n

import com.aistudio.superapp.domain.model.AppLanguage

data class StudioStrings(
    val chat: String, val images: String, val vision: String, val photo: String, val voice: String, val settings: String,
    val newChat: String, val sessions: String, val search: String, val summarize: String, val clear: String,
    val exportMarkdown: String, val exportJson: String, val send: String, val messageHint: String,
    val attach: String, val camera: String, val gallery: String, val recording: String, val typing: String,
    val prompt: String, val generate: String, val promptTemplates: String, val quality: String, val aspectRatio: String,
    val sendToChat: String, val download: String, val share: String, val analyze: String, val visionHint: String,
    val chooseImage: String, val crop: String, val rotate: String, val grayscale: String, val sepia: String,
    val reset: String, val aiModify: String, val inpaintHint: String, val startRecording: String, val stopRecording: String,
    val transcribe: String, val speak: String, val apiKeys: String, val models: String, val diagnostics: String,
    val language: String, val theme: String, val save: String, val test: String, val latency: String,
    val addModel: String, val modelId: String, val modelName: String, val baseUrl: String, val provider: String,
    val chatCapability: String, val visionCapability: String, val imageCapability: String, val audioCapability: String,
    val rename: String, val insertSummary: String, val copy: String, val cancel: String, val summary: String,
    val searchAllChats: String, val noResults: String, val error: String, val done: String,
    val ok: String, val more: String, val model: String, val imageModel: String, val visionModel: String, val sttModel: String,
    val fullPrompt: String, val securityNote: String, val paintArea: String,
    val systemTheme: String, val lightTheme: String, val darkTheme: String, val amoledTheme: String, val dynamicTheme: String,
)

fun stringsFor(language: AppLanguage): StudioStrings = if (language == AppLanguage.PERSIAN) fa else en

private val en = StudioStrings(
    chat="Chat", images="Images", vision="Vision", photo="Photo", voice="Voice", settings="Settings",
    newChat="New chat", sessions="Sessions", search="Search", summarize="Summarize session", clear="Clear chat",
    exportMarkdown="Export Markdown", exportJson="Export JSON", send="Send", messageHint="Message AI Studio…",
    attach="Attach", camera="Camera", gallery="Gallery", recording="Recording…", typing="AI is typing…",
    prompt="Prompt", generate="Generate", promptTemplates="Prompt templates", quality="Quality", aspectRatio="Aspect ratio",
    sendToChat="Send to Chat", download="Download", share="Share", analyze="Analyze", visionHint="Ask what you want to understand from this image…",
    chooseImage="Choose image", crop="Crop", rotate="Rotate", grayscale="Grayscale", sepia="Sepia", reset="Reset",
    aiModify="AI modify / inpaint", inpaintHint="Describe what to change in the painted area…", startRecording="Record", stopRecording="Stop",
    transcribe="Transcribe", speak="Speak", apiKeys="API keys", models="Model registry", diagnostics="Diagnostics",
    language="Language", theme="Theme", save="Save", test="Test", latency="Latency", addModel="Add model",
    modelId="Model ID", modelName="Display name", baseUrl="Base URL", provider="Provider", chatCapability="Chat",
    visionCapability="Vision", imageCapability="Image", audioCapability="Audio", rename="Rename", insertSummary="Insert summary",
    copy="Copy", cancel="Cancel", summary="Summary", searchAllChats="Search all chats", noResults="No results",
    error="Error", done="Done", ok="OK", more="More", model="Model", imageModel="Image model", visionModel="Vision model", sttModel="STT model",
    fullPrompt="Full", securityNote="API keys are encrypted with Android Keystore before being written to DataStore.",
    paintArea="Paint over the area you want the AI to replace.",
    systemTheme="System", lightTheme="Light", darkTheme="Dark", amoledTheme="AMOLED", dynamicTheme="Dynamic",
)

private val fa = StudioStrings(
    chat="گفتگو", images="تصویر", vision="بینایی", photo="ویرایش عکس", voice="صدا", settings="تنظیمات",
    newChat="گفتگوی جدید", sessions="گفتگوها", search="جستجو", summarize="خلاصه‌سازی گفتگو", clear="پاک‌کردن گفتگو",
    exportMarkdown="خروجی Markdown", exportJson="خروجی JSON", send="ارسال", messageHint="پیام به AI Studio…",
    attach="پیوست", camera="دوربین", gallery="گالری", recording="در حال ضبط…", typing="هوش مصنوعی در حال نوشتن…",
    prompt="پرامپت", generate="ساخت تصویر", promptTemplates="قالب‌های پرامپت", quality="کیفیت", aspectRatio="نسبت تصویر",
    sendToChat="ارسال به گفتگو", download="ذخیره", share="اشتراک‌گذاری", analyze="تحلیل", visionHint="درباره این تصویر چه چیزی می‌خواهید بدانید؟",
    chooseImage="انتخاب تصویر", crop="برش", rotate="چرخش", grayscale="سیاه‌وسفید", sepia="سپیا", reset="بازنشانی",
    aiModify="ویرایش هوشمند / Inpaint", inpaintHint="تغییری را که در ناحیه علامت‌خورده می‌خواهید توضیح دهید…", startRecording="ضبط", stopRecording="توقف",
    transcribe="تبدیل گفتار به متن", speak="خواندن متن", apiKeys="کلیدهای API", models="رجیستری مدل‌ها", diagnostics="عیب‌یابی اتصال",
    language="زبان", theme="پوسته", save="ذخیره", test="آزمایش", latency="تاخیر", addModel="افزودن مدل",
    modelId="شناسه مدل", modelName="نام نمایشی", baseUrl="آدرس پایه", provider="ارائه‌دهنده", chatCapability="گفتگو",
    visionCapability="بینایی", imageCapability="تصویر", audioCapability="صدا", rename="تغییر نام", insertSummary="درج خلاصه",
    copy="کپی", cancel="لغو", summary="خلاصه", searchAllChats="جستجو در همه گفتگوها", noResults="نتیجه‌ای پیدا نشد",
    error="خطا", done="انجام شد", ok="تأیید", more="بیشتر", model="مدل", imageModel="مدل تصویر", visionModel="مدل بینایی", sttModel="مدل تبدیل گفتار به متن",
    fullPrompt="کامل", securityNote="کلیدهای API پیش از ذخیره در DataStore با Android Keystore رمزنگاری می‌شوند.",
    paintArea="روی ناحیه‌ای که می‌خواهید هوش مصنوعی جایگزین کند با انگشت بکشید.",
    systemTheme="سیستم", lightTheme="روشن", darkTheme="تیره", amoledTheme="AMOLED", dynamicTheme="پویا",
)
