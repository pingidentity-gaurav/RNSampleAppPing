# React Native Ping SDK (POC)

## Run Instructions

For android:
```bash
npm run clean
npm install
npm run codegen
npm run android
```

For ios:
```bash
npm run clean
npm install
npm run codegen
bundle install
cd ios && bundle exec pod install
npm run ios
```