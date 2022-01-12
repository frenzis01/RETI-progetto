import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public @Data class Transaction {
    String ts;
    Double value;

    public Transaction(String ts, double value) {
        this.ts = ts;
        this.value = value;
    }
}
