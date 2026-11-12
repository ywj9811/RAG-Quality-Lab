import { Route, Routes } from "react-router";
import VisitorHome from "./pages/VisitorHome";
import AdminHome from "./pages/AdminHome";
import NotFound from "./pages/NotFound";

// 라우트 구성은 docs/phase0/06_wireframes.md 1절을 따른다. 화면은 각 Phase에서 채운다.
export default function App() {
  return (
    <Routes>
      <Route path="/" element={<VisitorHome />} />
      <Route path="/admin/*" element={<AdminHome />} />
      <Route path="*" element={<NotFound />} />
    </Routes>
  );
}
