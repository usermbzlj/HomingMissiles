package net.kyori.adventure.text;
import net.kyori.adventure.key.Key;
public interface Component {
 static Component text(String content) { return null; }
 static Component empty() { return null; }
 Component font(Key key);
}
