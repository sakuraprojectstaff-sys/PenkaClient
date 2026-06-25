package fun.rich.utils.features.autobuy;

import java.util.Locale;
import java.util.Objects;

public class BuyRequest {
    public String itemName;
    public int price;

    public BuyRequest(String itemName, int price) {
        this.itemName = itemName == null ? "" : itemName;
        this.price = Math.max(0, price);
    }

    public String key() {
        return itemName.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BuyRequest other)) return false;
        return price == other.price && Objects.equals(key(), other.key());
    }

    @Override
    public int hashCode() {
        return Objects.hash(key(), price);
    }

    @Override
    public String toString() {
        return "BuyRequest{itemName='" + itemName + "', price=" + price + "}";
    }
}
