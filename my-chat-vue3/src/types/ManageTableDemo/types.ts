import type { TestWarehouseGood } from "../models";

export interface WarehouseGoodQueryDTO {
    itemName?: string;
    startPrice?: number;
    endPrice?: number;
    startCreateTime?: string;
    endCreateTime?: string;
}

export interface WarehouseItemOrderVO {
    id: string;
    itemId: string;
    amount: number;
    location: string;
}

export interface WarehouseGoodVO extends TestWarehouseGood {
    creatorName: string;
    orders: WarehouseItemOrderVO[];
}