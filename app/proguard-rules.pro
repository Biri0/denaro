# WorkManager instantiates input mergers by class name before running Glance workers.
# Its 2.7.1 consumer rules keep the classes but not their constructors under R8 full mode.
-keep class * extends androidx.work.InputMerger {
    public <init>();
}
