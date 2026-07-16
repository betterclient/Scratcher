import sensing;

float lastTime = -1.0;
float currentFps = 0.0;
float smoothedFps = 0.0;

const float SMOOTHING_FACTOR = 0.1;

void update() {
    float now = sensing::getTimer();

    if (lastTime < 0.0) {
        lastTime = now;
        return;
    }

    float deltaTime = now - lastTime;
    lastTime = now;

    if (deltaTime > 0.0) {
        currentFps = 1.0 / deltaTime;

        if (smoothedFps == 0.0) {
            smoothedFps = currentFps;
        } else {
            smoothedFps = (currentFps * SMOOTHING_FACTOR) + (smoothedFps * (1.0 - SMOOTHING_FACTOR));
        }
    }
}

float get() {
    return smoothedFps;
}

float getRaw() {
    return currentFps;
}