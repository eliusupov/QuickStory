-- Tulcus (1052104): owner-requested 100% price increase.
-- Source: the current live shop baseline (431 rows; prices 35,000 through 1,000,000).
UPDATE shopitems
SET price = price * 2
WHERE shopid = 1052104;
