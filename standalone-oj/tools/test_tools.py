import copy,importlib.util,json,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def module(name):
    s=importlib.util.spec_from_file_location(name,ROOT/'tools'/f'{name}.py');m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m
oj=module('ojctl');generator=module('generate_example');initializer=module('init')
class ToolsTest(unittest.TestCase):
    def test_example_pack_and_editorial_is_valid(self):
        pack=oj.read(ROOT/'examples/original-sum/pack.json');oj.lint(pack)
        oj.lint_solution(oj.read(ROOT/'examples/original-sum/solution.json'),pack)
    def test_invalid_language_driver_and_conflicting_cases_fail(self):
        base=oj.read(ROOT/'examples/original-sum/pack.json')
        bad=copy.deepcopy(base);del bad['references']['rust']
        with self.assertRaises(oj.Error):oj.lint(bad)
        bad=copy.deepcopy(base);bad['modes'].append('FUNCTION');bad['profiles']['cpp']['functionDriver']='__USER_CODE__ __USER_CODE__'
        with self.assertRaises(oj.Error):oj.lint(bad)
        bad=copy.deepcopy(base);row=copy.deepcopy(bad['cases'][0]);row['name']='conflicting';row['expectedOutput']='different';bad['cases'].append(row)
        with self.assertRaises(oj.Error):oj.lint(bad)
    def test_duplicate_json_keys_are_not_silently_accepted(self):
        with tempfile.TemporaryDirectory() as folder:
            p=Path(folder)/'bad.json';p.write_text('{"slug":"a","slug":"b"}')
            with self.assertRaises(oj.Error):oj.read(p)
    def test_token_is_bound_to_one_site(self):
        with tempfile.TemporaryDirectory() as folder:
            p=Path(folder)/'token.json';p.write_text(json.dumps({'baseUrl':'https://one.invalid','token':'unit-test-placeholder'}))
            with self.assertRaises(oj.Error):oj.Client('https://two.invalid',p)
        with self.assertRaises(oj.Error):oj.NoRedirect().redirect_request(None,None,None,None,None,None)
    def test_generator_is_reproducible_and_detects_simple_mutants(self):
        with tempfile.TemporaryDirectory() as folder:
            a,b=Path(folder)/'a',Path(folder)/'b';generator.generate(a,'original-example',42);generator.generate(b,'original-example',42)
            self.assertEqual((a/'pack.json').read_bytes(),(b/'pack.json').read_bytes())
            pack=oj.read(a/'pack.json');oj.lint(pack);oj.lint_solution(oj.read(a/'solution.json'),pack)
            values=[tuple(map(int,c['input'].split())) for c in pack['cases']]
            for mutation in (lambda a,b:a-b,lambda a,b:0,lambda a,b:a,lambda a,b:abs(a)+abs(b)):
                self.assertTrue(any(mutation(a,b)!=a+b for a,b in values))
            with self.assertRaises(ValueError):generator.generate(a,'original-example',42)
    def test_private_configuration_is_not_overwritten(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);initializer.initialize(root,'admin');before=(root/'.env').read_bytes()
            self.assertNotIn('REDIS_',before.decode())
            self.assertIn('LUME_IMAGE=pllysun/lume:',before.decode())
            self.assertEqual(0o600,(root/'.env').stat().st_mode & 0o777)
            self.assertEqual(0o700,(root/'secrets').stat().st_mode & 0o777)
            self.assertGreaterEqual(len((root/'secrets/judger-token').read_text().strip()),32)
            with self.assertRaises(ValueError):initializer.initialize(root,'admin')
            self.assertEqual(before,(root/'.env').read_bytes())
    def test_slug_lookup_uses_exact_match_across_server_pages(self):
        client=object.__new__(oj.Client);seen=[]
        def request(method,path):
            self.assertEqual('GET',method);self.assertNotIn('keyword=',path)
            page=int(oj.urllib.parse.parse_qs(oj.urllib.parse.urlsplit(path).query)['page'][0]);seen.append(page)
            records=[{'id':i,'slug':'other-'+str(i)} for i in range(50)] if page==1 else [{'id':51,'slug':'exact-slug'}]
            return {'records':records,'total':51,'size':50}
        client.request=request
        self.assertEqual(51,client.find('exact-slug')['id']);self.assertEqual([1,2],seen)
        seen.clear();self.assertIsNone(client.find('exact-slug-extra'));self.assertEqual([1,2],seen)
if __name__=='__main__':unittest.main()
