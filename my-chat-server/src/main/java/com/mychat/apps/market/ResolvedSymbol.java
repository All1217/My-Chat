package com.mychat.apps.market;

/** 识别后的证券代码：A 股带交易所前缀，美股为大写 ticker。 */
public record ResolvedSymbol(String symbol, String market, String eastMoneySecId) {

    public boolean cn() {
        return "CN".equals(market);
    }
}
