import { Route, Routes } from "react-router";
import VisitorChat from "./pages/VisitorChat";
import AdminHome from "./pages/AdminHome";
import AdminLayout from "./pages/admin/AdminLayout";
import AdminLogin from "./pages/admin/AdminLogin";
import AdminDocuments from "./pages/admin/AdminDocuments";
import AdminChats from "./pages/admin/AdminChats";
import AdminChatDetail from "./pages/admin/AdminChatDetail";
import AdminEvaluation from "./pages/admin/AdminEvaluation";
import NotFound from "./pages/NotFound";

// 라우트 구성은 docs/phase0/06_wireframes.md 1절을 따른다.
export default function App() {
  return (
    <Routes>
      <Route path="/" element={<VisitorChat />} />
      <Route path="/admin/login" element={<AdminLogin />} />
      <Route path="/admin" element={<AdminLayout />}>
        <Route index element={<AdminHome />} />
        <Route path="documents" element={<AdminDocuments />} />
        <Route path="chats" element={<AdminChats />} />
        <Route path="chats/:id" element={<AdminChatDetail />} />
        <Route path="evaluations/:id" element={<AdminEvaluation />} />
      </Route>
      <Route path="*" element={<NotFound />} />
    </Routes>
  );
}
