import copy
import json
import unittest
import build_shop_locations as sites

class ShopSiteTest(unittest.TestCase):
    def setUp(self):
        self.pin=json.loads((sites.PINS/'Forestry_Shop.json').read_text(encoding='utf8'))
        self.offers=json.loads((sites.DATA/'wiki_shop_sources.json').read_text(encoding='utf8'))

    def test_reproduce_shop_sites_and_preserve_the_location_offer_distinction(self):
        data=sites.build()
        self.assertEqual(data,json.loads(sites.OUTPUT.read_text(encoding='utf8')))
        forest=next(s for s in data['shops'] if s['sourceName']=='Forestry Shop')
        self.assertEqual(4,len(forest['sites']))
        self.assertEqual(21,len(forest['offers']))
        self.assertFalse(forest['sites'][0]['membersShop'])
        self.assertTrue(next(o['itemMembers'] for o in forest['offers'] if o['itemName']=='Cape pouch'))

    def test_bulk_contents_are_exactly_the_two_infinite_slayer_stock_rows(self):
        data = sites.build()
        offers = [(shop["sourceName"], row) for shop in data["shops"] for row in shop["offers"] if "bulkPurchase" in row]
        self.assertEqual(2, len(offers))
        for shop, row in offers:
            item = row["bulkPurchase"]["itemName"]
            self.assertEqual("Slayer Rewards", shop)
            self.assertIn(item, ("Broad bolts", "Broad arrows"))
            self.assertEqual(item + " (x250)", row["itemName"])
            self.assertEqual(250, row["bulkPurchase"]["quantity"])
            self.assertEqual("35 Slayer points", row["cost"])

    def test_ambiguous_or_already_located_offers_do_not_acquire_a_site_map(self):
        row=next(r for r in self.offers if r['sourceName']=='Forestry Shop' and r['itemName']=='Cape pouch')
        self.offers.append(copy.deepcopy(row))
        for r in self.offers:
            if r['itemName']=='Forestry kit' and r['sourceName']=='Forestry Shop':r['location']='Different branch'
        mapped=sites.extract(self.pin,self.offers)['offers']
        self.assertFalse(any(r['itemName'] in ('Cape pouch','Forestry kit') for r in mapped))
