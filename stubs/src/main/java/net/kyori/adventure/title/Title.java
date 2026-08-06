package net.kyori.adventure.title;

import net.kyori.adventure.text.Component;
import java.time.Duration;

public interface Title {
    static Title title(Component title, Component subtitle, Times times) { return null; }
    interface Times {
        static Times times(Duration fadeIn, Duration stay, Duration fadeOut) { return null; }
    }
}
