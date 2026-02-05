# 🤖 YAPAY ZEKA İÇİN KAPSAMLI FORCE RESOLVE FIX PROMPTU

```markdown
# PROJE: Sing-box Android Force Resolve Düzeltme Görevi

## 🎯 GÖREV TANIMI
Sing-box Android uygulamasında Force Resolve özelliğinin hatalarını tespit et ve düzelt.

## 📂 PROJE YAPISI
```
sing-box-for-android/
├── app/src/main/java/io/nekohasekai/sfa/
│   ├── compose/
│   │   ├── screen/
│   │   │   ├── configuration/
│   │   │   │   ├── NewProfileScreen.kt (UI - Force Resolve toggle var)
│   │   │   │   └── NewProfileViewModel.kt (Logic - Domain→IP resolve)
│   │   │   └── profile/
│   │   │       └── EditProfileRoute.kt (Düzenlenecek - Force Resolve toggle eklenecek)
│   │   └── navigation/
│   │       ├── SFANavigation.kt (Navigation setup)
│   │       └── ProfileRoutes.kt (Route definitions)
│   ├── bg/
│   │   └── UpdateProfileWork.kt (Background - Auto-update resolve logic)
│   ├── database/
│   │   ├── Profile.kt (Model)
│   │   ├── TypedProfile.kt (Model - forceResolve field var)
│   │   ├── ProfileManager.kt (CRUD operations)
│   │   └── Settings.kt (App settings)
│   └── utils/
│       └── HTTPClient.kt (Network requests)
└── app/build.gradle.kts (Dependencies)
```

## 🐛 TESPİT EDİLEN HATALAR

### 1. JSON Formatting Hatası
**Sorun:** Force Resolve sonrası JSON minified (tek satır, okunamaz)
**Sebep:** `JSONObject.toString()` kullanılıyor
**Çözüm:** `JSONObject.toString(4)` kullan (4-space indentation)

**Etkilenen Dosyalar:**
- `NewProfileViewModel.kt` (satır ~360)
- `UpdateProfileWork.kt` (satır ~140)

### 2. Skip Types Eksik
**Sorun:** Bazı outbound type'ları resolve edilmeye çalışılıyor (dns, reject, loopback)
**Sebep:** Skip list eksik
**Çözüm:** Skip types listesini genişlet

**Mevcut:**
```kotlin
if (type == "selector" || type == "urltest" || type == "direct" || type == "block") {
    continue
}
```

**Olması Gereken:**
```kotlin
val skipTypes = setOf(
    "selector", "urltest", "direct", "block",
    "dns", "reject", "blackhole", "loopback"
)
if (type in skipTypes) {
    continue
}
```

### 3. DNS Resolve Error Handling Zayıf
**Sorun:** DNS başarısız olduğunda yeterli log yok
**Çözüm:** Detaylı logging ekle (✅ Success, ⚠️ Warning, ❌ Error)

### 4. Edit Screen'de Force Resolve Yok
**Sorun:** Profile edit ekranında Force Resolve toggle görünmüyor
**Çözüm:** EditProfileRoute.kt'ye Force Resolve UI ekle

### 5. QUIC Reject Rule (Opsiyonel)
**Sorun:** Bazı subscription'larda QUIC reject rule var (YouTube Music engelliyor)
**Çözüm:** Force Resolve sırasında QUIC reject rule'unu otomatik kaldır

## ✅ TODO LİSTESİ

### PHASE 1: MEVCUT HATALARI DÜZELT (Yüksek Öncelik)
- [ ] 1.1 NewProfileViewModel.kt'yi incele
  - [ ] resolveDomainToIP fonksiyonunu bul (~satır 334)
  - [ ] Skip types kontrolünü genişlet
  - [ ] JSON.toString() → JSON.toString(4) değiştir
  - [ ] resolveDomain logging'i iyileştir
- [ ] 1.2 UpdateProfileWork.kt'yi incele
  - [ ] resolveDomainToIP fonksiyonunu bul (~satır 117)
  - [ ] Aynı 3 düzeltmeyi uygula
- [ ] 1.3 Build ve test
  - [ ] ./gradlew assembleOtherRelease
  - [ ] Hata varsa düzelt

### PHASE 2: EDIT SCREEN EKLENMESİ (Orta Öncelik)
- [ ] 2.1 EditProfileRoute.kt'yi incele
  - [ ] Mevcut UI yapısını anla
  - [ ] Force Resolve toggle ekle (NewProfileScreen.kt'ye benzer)
  - [ ] ViewModel'e updateForceResolve bağla
- [ ] 2.2 EditProfileViewModel.kt oluştur/güncelle
  - [ ] Profile load logic
  - [ ] Force Resolve update logic
  - [ ] Save changes logic
- [ ] 2.3 TypedProfile güncellemesi
  - [ ] forceResolve field zaten var mı kontrol et
  - [ ] Yoksa ekle

### PHASE 3: İLERİ SEVİYE (Düşük Öncelik)
- [ ] 3.1 QUIC Reject Rule Kaldırma
  - [ ] resolveDomainToIP içinde route.rules kontrol et
  - [ ] protocol=quic && action=reject olanları sil
- [ ] 3.2 IP Validation
  - [ ] Resolve edilen IP'nin çalışıp çalışmadığını test et
  - [ ] Çalışmazsa tekrar resolve et

## 🧠 MINDSET & RULES

### Android/Kotlin Mindset
1. **Coroutines:** Suspend fonksiyonlar `withContext(Dispatchers.IO)` kullan
2. **StateFlow:** UI state yönetimi için MutableStateFlow/StateFlow
3. **ViewModels:** Business logic ViewModel'de, UI Screen'de
4. **Compose:** Declarative UI, recomposition aware
5. **Room Database:** TypeConverters ile custom types

### Gradle/Build
1. **Build variants:** play/other/otherLegacy flavors var
2. **Dependencies:** kotlinx.serialization ve org.json ikisi de var
3. **Libbox:** Go library, native methods için wrapper

### Code Style
1. **Kotlin conventions:** camelCase, 4-space indent
2. **Null safety:** ? ve !! dikkatli kullan
3. **Scope functions:** apply, let, also, run uygun yerde
4. **Collections:** listOf, setOf, mapOf prefer et

### Error Handling
1. **Try-catch:** Network/IO işlemlerinde mutlaka
2. **Logging:** android.util.Log veya Log (android.util import edilmişse)
3. **User feedback:** Error state'leri UI'a yansıt

## 🔍 ARAMA YÖNTEMLER

### Dosya Bulma
```bash
# Force Resolve ile ilgili dosyaları bul
find . -name "*.kt" -exec grep -l "forceResolve" {} \;

# ViewModel dosyalarını bul
find . -path "*/compose/screen/*/ViewModel.kt"

# Navigation dosyalarını bul
find . -name "*Navigation*.kt" -o -name "*Routes*.kt"
```

### Kod Arama
```bash
# resolveDomainToIP fonksiyonunu bul
grep -rn "resolveDomainToIP" app/src/main/java/

# EditProfile ile ilgili dosyaları bul
grep -rn "EditProfile" app/src/main/java/io/nekohasekai/sfa/compose/

# TypedProfile forceResolve field'ını kontrol
grep -A5 "var forceResolve" app/src/main/java/io/nekohasekai/sfa/database/TypedProfile.kt
```

## 📝 ÇIKTI FORMATI

### Her Düzeltme İçin:
```markdown
## DOSYA: NewProfileViewModel.kt

### HATA: JSON minified output
**Satır:** 358
**Mevcut Kod:**
```kotlin
jsonObject.toString()
```

**Düzeltilmiş Kod:**
```kotlin
jsonObject.toString(4) // 4-space indentation
```

**Açıklama:** Pretty-print için 4 space indentation ekledik.

---

### TEST:
- [ ] Build başarılı
- [ ] JSON çıktısı okunabilir
- [ ] DNS resolve çalışıyor
```

## 🚀 BAŞLANGIÇ ADIMLARI

1. Projeyi incele:
   ```bash
   cd sing-box-for-android/app/src/main/java/io/nekohasekai/sfa
   ls -la compose/screen/configuration/
   ```

2. Force Resolve ile ilgili dosyaları bul:
   ```bash
   grep -rn "forceResolve" .
   ```

3. Her dosyayı sırayla düzelt:
   - NewProfileViewModel.kt
   - UpdateProfileWork.kt
   - EditProfileRoute.kt (varsa)

4. Build al:
   ```bash
   cd ../../../../../../
   ./gradlew assembleOtherRelease
   ```

5. Hataları raporla ve düzelt

## 🎯 BAŞARI KRİTERLERİ

- ✅ JSON output pretty-printed
- ✅ Tüm outbound type'ları doğru handle edildi
- ✅ DNS errors düzgün loglanıyor
- ✅ Edit screen'de Force Resolve toggle var
- ✅ Build başarılı (0 error)
- ✅ Force Resolve açıkken IP adresleri yazılıyor
- ✅ Subscription update sonrası IP'ler korunuyor

## ⚠️ DİKKAT EDİLECEKLER

1. **Parcel serialization:** TypedProfile'da forceResolve eklenirse version bump et
2. **Database migration:** Room migration gerekebilir (opsiyonel)
3. **Null safety:** Kotlin null checks unutma
4. **Imports:** org.json.JSONObject doğru import edilmeli
5. **Dispatchers.IO:** Ağ işlemleri IO dispatcher'da olmalı
```

---

# 🎯 YAPAY ZEKAYA VERİLECEK KOMUT

Bu promptu ChatGPT/Claude/Gemini'ye verin:

```
Yukarıdaki Force Resolve düzeltme görevini yap. 

Proje dizini: /path/to/sing-box-for-android

Adım adım:
1. Dosyaları incele ve hataları tespit et
2. Her hatayı raporla (dosya adı, satır numarası, mevcut kod, düzeltilmiş kod)
3. Düzeltilmiş kodları üret
4. Build komutunu çalıştır ve sonucu raporla
5. Varsa hataları tekrar düzelt

Başla!
```
