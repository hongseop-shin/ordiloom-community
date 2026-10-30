# ORDILOOM Community

An offline Android application for customer, product, inventory, and order management.
Built with Java, Android framework views, and SQLite. English and Korean interfaces are included.

**Author:** Hongseop Shin · [Google Play edition](https://play.google.com/store/apps/details?id=com.hongseop.ordiloom)

## Features

- Customer records and product catalog with SKU, size ratios, pack quantities, and stock.
- Draft, complete, cancel, and duplicate orders.
- Item/order discounts, recorded payments, balances, notes, and optional drawn signatures.
- Stock adjustments when orders are completed or cancelled.
- JSON backup and restore using Android's document picker.
- Local storage without an account, backend, advertising, analytics SDK, or network permission.

Prices are in USD. The app records payments; it does not process payments or calculate taxes.
Backups may contain customer information and signatures. Restoring a backup replaces local data.
Uninstalling deletes local app data; export a backup first.

## Community and store editions

| Item | Community source | Google Play edition |
| --- | --- | --- |
| Customers, products, inventory, orders | Included | Included |
| JSON backup / restore | Included | Included |
| Signature capture | Included | Included |
| PDF receipt generation | Excluded | Included |
| App ID | `com.hongseop.ordiloom.community` | `com.hongseop.ordiloom` |
| Store signing keys and release binaries | Not included | Managed separately |

This repository contains a functional source subset derived from version 0.1.0, not the complete store release source. The community app has its own data sandbox and can be installed alongside the store app. It does not automatically access the store app's data. Backup identifiers are retained from the original project; cross-edition migration has not been tested.

## Build

Requirements: JDK 17, Python 3, Android SDK Platform 36, and Android SDK Build-Tools 36.0.0.
Install these through Android Studio's SDK Manager. Set the SDK path appropriate for your machine.

The direct build script avoids Gradle plugin compatibility differences:

```sh
python3 tools/build.py --sdk /absolute/path/to/Android/Sdk
```

On Windows, use `python` instead of `python3` if appropriate and quote paths containing spaces.
The script creates a local signing key and password under `signing/` and writes
`dist/ORDILOOM-Community-0.1.0.apk`. Keep `signing/` private. It is ignored by Git.
You do not need the Google Play edition's signing key.

The included Gradle project and wrapper are an alternative development setup:

```sh
./gradlew :app:assembleDebug
# Windows: gradlew.bat :app:assembleDebug
```

Gradle requires internet access to download its tools. The inherited Gradle configuration has not been validated for this community edition; the direct SDK script is the preferred path.

## Tests and verification

```sh
python3 tools/test.py
```

Community preparation checks passed: 43 Java domain assertions (money, quantities, discounts, payment limits) plus SQLite constraints and transaction rollback checks.
Android SDK tools were unavailable in the packaging environment, so this community variant has **not yet been compiled or run on a device**. The original store release's validation does not establish that this modified variant is validated.

Before distributing a community APK, build and check: launch, create a customer/product, save a draft, complete/cancel an order and inspect stock, capture a signature, export/restore a backup, restart, and switch English/Korean. Use disposable test data for restore tests.

## Source map

- `MainActivity.java`: native Android screens, forms, navigation, and document picker.
- `Store.java`: SQLite schema, transactions, order lifecycle, backup validation.
- `Money.java` and `Rules.java`: money and quantity validation.
- `SignatureView.java`: local signature capture.
- `tests/`: domain and Android repository checks.
- `tools/`: build and test scripts.

Sources are under `app/src/main/java/com/hongseop/ordiloom/community/`.
The project was developed with AI assistance; implementation and test coverage should be reviewed like any other contribution.

## Contributing

Open an issue describing reproducible behavior, expected behavior, and Android version.
Use fictional customer data in examples. Run `python3 tools/test.py` before a pull request.
Never include signing files, passwords, customer backups, or production databases.

## License

Original source code and documentation are provided under the MIT License; see `LICENSE`.
The Gradle wrapper retains its existing third-party license in `THIRD_PARTY_LICENSE_GRADLE.txt`.
The ORDILOOM name and existing logo are excluded from the MIT grant; see `BRANDING.md`.
