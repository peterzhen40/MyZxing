# MyZxing

### 说明：
从官方zxing中更改，简化代码，优化速度，优化UI，解耦CaptureActivity

### How to use
#### Step 1. Add the JitPack repository to your build file
Add it in your root build.gradle at the end of repositories:

```
allprojects {
    repositories {
      ...
      maven { url 'https://jitpack.io' }
    }
}
```

#### Step 2. Add the dependency
[![](https://jitpack.io/v/peterzhen40/MyZxing.svg)](https://jitpack.io/#peterzhen40/MyZxing)

```
dependencies {
     implementation 'com.github.peterzhen40:MyZxing:1.0.0'
}