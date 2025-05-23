# sphinx-android-v2

## Cloning
This repository uses git submodules (for the time being). When cloning the repository, run:
```
git clone --recursive https://github.com/stakwork/sphinx-android-v2.git
```

If you've already cloned the repository, run:
```
git checkout master
git pull
git submodule update --init
```

In order to keep the submodules updated when pulling the latest code, run:
```
git pull --recurse-submodules
```

To update only the submodules, run:
```
git submodule update
```

## Building The App
See [HERE](./docs/NOTIFICATIONS.md) to enable building Sphinx with FirebaseMessaging
See [HERE](./docs/GIPHY.md) to enable building Sphinx with the Giphy SDK  
See [HERE](./docs/YOUTUBE.md) to enable building Sphinx with the YouTube Player API
  
Checkout branch `master`:
```bash
git checkout master
git pull --recurse-submodule
```  

The Application module to build is located at `sphinx/application/sphinx`. To Build, you can either:
 - Use Android Studio
 - From command line via: `./gradlew :sphinx:application:sphinx:build`

## History
This project was created from a branch of Sphinx Android v1 repository.  
You can check the code history of this repository [HERE](https://github.com/stakwork/sphinx-kotlin/tree/development-v2-merged)

For more detailed instructions, see [HERE](./docs/RELEASING.md#building-a-release)
