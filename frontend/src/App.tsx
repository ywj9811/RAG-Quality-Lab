import { Route, Routes } from "react-router";
import VisitorHome from "./pages/VisitorHome";
import AdminHome from "./pages/AdminHome";
import AdminLayout from "./pages/admin/AdminLayout";
import AdminLogin from "./pages/admin/AdminLogin";
import AdminDocuments from "./pages/admin/AdminDocuments";
import NotFound from "./pages/NotFound";

// 라우트 구성은 docs/phase0/06_wireframes.md 1절을 따른다.
export default function App() {
  return (
    <Routes>
      <Route path="/" element={<VisitorHome />} />
      <Route path="/admin/login" element={<AdminLogin />} />
      <Route path="/admin" element={<AdminLayout />}>
        <Route index element={<AdminHome />} />
        <Route path="documents" element={<AdminDocuments />} />
      </Route>
      <Route path="*" element={<NotFound />} />
    </Routes>
  );
}
