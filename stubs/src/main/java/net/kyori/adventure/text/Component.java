package net.kyori.adventure.text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.ShadowColor;
public interface Component {
 static Component text(String content) { return null; }
 static Component empty() { return null; }
 Component font(Key key);
 Component shadowColor(ShadowColor shadowColor);
}
