# mangadexdroid

Latest release APK can be downloaded [here](https://github.com/communistWatermelon/mangadexdroid/releases). 
New commits on `main` are built using Github Actions.

## Preview

Main Screen             |  Notification
:-------------------------:|:-------------------------:
<img src="https://user-images.githubusercontent.com/3271813/198726818-79539afe-564a-4d87-8ecd-116853d9481a.png" width="200">  |  <img src="https://user-images.githubusercontent.com/3271813/198822770-9a124ecb-5215-4dc2-9029-50a5909eb014.png" width="200">

## Summary
Simple Android project that notifies you of new chapters to manga series followed on mangadex.org. Mostly created so I could avoid using third-party RSS feeds, but also as a quick review of Android.
Mostly only maintained for my own use, feel free to fork the project and build on it if necessary. I will likely not be adding any more features, and will not build an iOS version.

The app will only notify you of chapters that have been released *after* you've installed the app. This is to prevent notification spam on first run.

### Working Functionality
- Logging in
- Tracking/Displaying followed Manga updates from MangaDex
- Notifying of chapters released after install date
- Opening a new manga chapter from the Android notification or from the home screen
- Light and Dark theme (following Android's system setting)
- Material You theming (on supported devices)

### Missing Functionality
- Log out (clear app data if you want to log out)
- Everything else related to MangaDex's site functionality.


## General Architecture
Developed using Jetpack Compose for the UI
Ktor for HTTP requests
Koin for Dependency Injection

App architecture is modelled after Google's recommended approach: Model-View-ViewModel (MVVM) with Repositories.


## API
Working directly with the MangaDex API. 
Using global rate-limiting in Ktor, matching MangaDex's 5 per second limit.

## Analytics/Logging

All logs + analytics are logged to my personal Firebase Crashlytics project, so I have information to go on in the event of a crash. 


## Sync Process

Every ~15 minutes (while the app is foregrounded OR background), the app will go through a sync process:
1. Refresh Auth Token
2. Fetch Followed Chapters
3. Fetch new Manga info for unknown manga series
4. Fetch covers for new manga found in step 3
5. Fetch chapter read status markers for all users known manga

Backgronud refreshes are using WorkManager, which means the update timing is more up to Android than me.


## Storage

Manga series and chapter information is stored on a local DB to avoid loading on app start, and because I hadn't done a lot of DB work previously.
Cover images are cached and stored on device.
